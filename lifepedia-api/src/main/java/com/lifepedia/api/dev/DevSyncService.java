package com.lifepedia.api.dev;

import com.lifepedia.api.dev.DevAutomation.Event;
import com.lifepedia.api.dev.DevItem.Kind;
import com.lifepedia.api.integration.AccountSyncer;
import com.lifepedia.api.integration.Connection;
import com.lifepedia.api.integration.IntegrationException;
import com.lifepedia.api.integration.IntegrationProvider;
import com.lifepedia.api.integration.code.CodeHost;
import com.lifepedia.api.integration.code.CodeHost.IssueRef;
import com.lifepedia.api.integration.code.CodeHost.RemoteActivity;
import com.lifepedia.api.integration.code.CodeHost.RemoteIssue;
import com.lifepedia.api.integration.code.CodeHost.RemoteMr;
import com.lifepedia.api.integration.code.CodeHost.RemotePipeline;
import com.lifepedia.api.integration.code.CodeHost.RemoteRepo;
import com.lifepedia.api.integration.code.CodeHost.Snapshot;
import com.lifepedia.api.integration.code.Pipelines;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import com.lifepedia.api.task.TaskLinks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sincroniza as contas GitLab/GitHub: retrato de MRs, issues e pipelines dos repositórios ligados, raias das issues
 * (etiquetas ao abrir e aceitar MR), tarefas das issues, avisos e atividade por dia.
 */
@Service
public class DevSyncService implements AccountSyncer {

    static final int ACTIVITY_DAYS = 14;
    /** Na primeira sincronização de um repositório busca mais dias, para o gráfico já nascer com histórico. */
    static final int FIRST_ACTIVITY_DAYS = 84;
    static final int MERGED_DAYS = 3;
    private static final Logger log = LoggerFactory.getLogger(DevSyncService.class);

    private final DevRepoRepository repos;
    private final DevProjectRepository projects;
    private final DevItemRepository items;
    private final DevAutomationRepository automations;
    private final DevActivityRepository activity;
    private final List<CodeHost> codeHosts;
    private final TaskLinks tasks;
    private final NotificationService notifications;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DevSyncService(DevRepoRepository repos, DevProjectRepository projects, DevItemRepository items,
                          DevAutomationRepository automations, DevActivityRepository activity, List<CodeHost> codeHosts,
                          TaskLinks tasks, NotificationService notifications, Clock clock, PlatformTransactionManager transactions) {
        this.repos = repos;
        this.projects = projects;
        this.items = items;
        this.automations = automations;
        this.activity = activity;
        this.codeHosts = codeHosts;
        this.tasks = tasks;
        this.notifications = notifications;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Override
    public boolean supports(IntegrationProvider provider) {
        return provider.isCodeHost();
    }

    @Override
    public Map<UUID, List<String>> usage(UUID userId) {
        Map<UUID, String> names = projects.findByUserIdOrderByPositionAscCreatedAtAsc(userId).stream()
                .collect(Collectors.toMap(DevProject::getId, DevProject::getName));
        Map<UUID, Set<String>> used = new LinkedHashMap<>();
        for (DevRepo repo : repos.findByUserIdOrderByCreatedAtAsc(userId)) {
            used.computeIfAbsent(repo.getAccountId(), k -> new LinkedHashSet<>()).add(names.get(repo.getProjectId()));
        }
        for (DevProject project : projects.findByUserIdOrderByPositionAscCreatedAtAsc(userId)) {
            if (project.getCalendarAccountId() != null) {
                used.computeIfAbsent(project.getCalendarAccountId(), k -> new LinkedHashSet<>()).add(project.getName());
            }
        }
        Map<UUID, List<String>> result = new HashMap<>();
        used.forEach((id, set) -> result.put(id, set.stream().filter(Objects::nonNull).toList()));
        return result;
    }

    @Override
    public int sync(Connection connection) throws IntegrationException {
        List<DevRepo> linked = transaction.execute(status -> repos.findByAccountId(connection.accountId()));
        if (linked == null || linked.isEmpty()) return 0;
        CodeHost host = codeHosts.stream().filter(h -> h.provider() == connection.provider()).findFirst()
                .orElseThrow(() -> IntegrationException.of(IntegrationException.Kind.UNAVAILABLE));
        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate today = now.atZoneSameInstant(clock.getZone()).toLocalDate();
        List<RemoteRepo> remotes = linked.stream()
                .map(r -> new RemoteRepo(r.getExternalId(), r.getFullName(), r.getWebUrl(), r.getDefaultBranch())).toList();
        Snapshot snapshot = host.snapshot(connection, remotes, now.minusDays(MERGED_DAYS));
        int window = linked.stream().anyMatch(r -> r.getSyncedAt() == null) ? FIRST_ACTIVITY_DAYS : ACTIVITY_DAYS;
        LocalDate from = today.minusDays(window - 1L);
        List<RemoteActivity> days = List.of();
        try {
            days = host.activity(connection, remotes.stream().map(RemoteRepo::externalId).toList(), from, clock.getZone());
        } catch (IntegrationException e) {
            log.info("Atividade da conta {} indisponível: {}", connection.accountId(), e.kind());
        }
        Map<String, DevRepo> byExternal = new HashMap<>();
        linked.forEach(r -> byExternal.put(r.getExternalId(), r));
        Map<UUID, Map<String, String>> lanes = connection.canWrite() ? moveLanes(connection, host, linked, snapshot) : Map.of();
        Set<String> closed = closedIssues(connection, host, linked, snapshot);
        List<RemoteActivity> activityDays = days;
        transaction.executeWithoutResult(status ->
                save(connection, linked, byExternal, snapshot, closed, lanes, activityDays, from, today, now));
        return snapshot.mergeRequests().size() + snapshot.issues().size() + snapshot.pipelines().size();
    }

    /** Raias: MR aberto ou aceito depois da regra põe a etiqueta nas issues dele (uma vez por MR e evento). */
    private Map<UUID, Map<String, String>> moveLanes(Connection connection, CodeHost host, List<DevRepo> linked, Snapshot snapshot) {
        Map<UUID, Map<String, String>> lanes = new HashMap<>();
        Map<String, DevRepo> byExternal = linked.stream().collect(Collectors.toMap(DevRepo::getExternalId, Function.identity()));
        for (RemoteMr mr : snapshot.mergeRequests()) {
            DevRepo repo = byExternal.get(mr.repoId());
            if (repo == null || !mr.mine() || mr.issues().isEmpty()) continue;
            Event event = laneEvent(repo, mr);
            if (event == null) continue;
            Set<String> done = transaction.execute(status -> automations.findByRepoId(repo.getId()).stream()
                    .map(a -> a.getMrId() + ":" + a.getEvent()).collect(Collectors.toSet()));
            if (done == null || done.contains(mr.id() + ":" + event)) continue;
            String label = event == Event.OPENED ? repo.getLabelMrOpened() : repo.getLabelMrMerged();
            List<String> remove = new ArrayList<>(repo.clearList());
            String other = event == Event.OPENED ? repo.getLabelMrMerged() : repo.getLabelMrOpened();
            if (other != null) remove.add(other);
            remove.removeIf(label::equalsIgnoreCase);
            try {
                for (IssueRef issue : mr.issues()) host.relabel(connection, issue, List.of(label), remove);
                String issues = mr.issues().stream().map(IssueRef::toString).collect(Collectors.joining(","));
                transaction.executeWithoutResult(status -> automations.save(new DevAutomation(repo.getId(), mr.id(), event, issues)));
                if (event == Event.MERGED) lanes.computeIfAbsent(repo.getId(), k -> new HashMap<>()).put(mr.id(), label);
            } catch (IntegrationException e) {
                log.info("Raia do MR {} não mudou: {}", mr.id(), e.kind());
            }
        }
        return lanes;
    }

    /** Issues que tinham tarefa e sumiram da lista: fechadas lá (conclui a tarefa) ou só desatribuídas. */
    private Set<String> closedIssues(Connection connection, CodeHost host, List<DevRepo> linked, Snapshot snapshot) {
        Set<String> present = snapshot.issues().stream().map(i -> i.repoId() + "#" + i.number()).collect(Collectors.toSet());
        Map<UUID, DevRepo> byId = linked.stream().collect(Collectors.toMap(DevRepo::getId, Function.identity()));
        List<DevItem> stored = transaction.execute(status -> items.findByRepoIdIn(byId.keySet()));
        Set<String> closed = new HashSet<>();
        for (DevItem item : stored == null ? List.<DevItem>of() : stored) {
            DevRepo repo = byId.get(item.getRepoId());
            String key = repo.getExternalId() + "#" + item.getNumber();
            if (item.getKind() != Kind.ISSUE || item.getTaskId() == null || present.contains(key)) continue;
            try {
                if (!host.issueOpen(connection, new IssueRef(repo.getExternalId(), item.getNumber()))) closed.add(key);
            } catch (IntegrationException e) {
                if (e.kind() == IntegrationException.Kind.NOT_FOUND) closed.add(key);
            }
        }
        return closed;
    }

    private void save(Connection connection, List<DevRepo> linked, Map<String, DevRepo> byExternal, Snapshot snapshot, Set<String> closed,
                      Map<UUID, Map<String, String>> lanes, List<RemoteActivity> days, LocalDate from, LocalDate today,
                      OffsetDateTime now) {
        Map<UUID, DevProject> projectById = new HashMap<>();
        for (DevRepo repo : linked) projects.findById(repo.getProjectId()).ifPresent(p -> projectById.put(p.getId(), p));
        Map<String, DevItem> stored = new HashMap<>();
        items.findByRepoIdIn(linked.stream().map(DevRepo::getId).toList())
                .forEach(i -> stored.put(i.getRepoId() + "|" + i.getKind() + "|" + i.getExternalId(), i));
        Set<String> seen = new HashSet<>();

        for (RemoteMr mr : snapshot.mergeRequests()) {
            DevRepo repo = byExternal.get(mr.repoId());
            if (repo == null) continue;
            String key = repo.getId() + "|" + Kind.MR + "|" + mr.id();
            seen.add(key);
            DevItem before = stored.get(key);
            Previous previous = before == null ? null : new Previous(before.getState(), before.getPipeline(), before.isApproved(),
                    before.getComments());
            DevItem item = before != null ? before : new DevItem(repo.getId(), connection.userId(), Kind.MR, mr.id());
            item.setBasics(mr.number(), mr.title(), mr.state(), mr.webUrl(), mr.author(), mr.updatedAt());
            String issues = mr.issues().stream().map(IssueRef::toString).collect(Collectors.joining(","));
            item.setMergeRequest(mr.mine(), mr.review(), mr.draft(), mr.branch(), mr.pipeline(), mr.approved(), mr.comments(),
                    issues.length() <= 500 ? issues : issues.substring(0, 500), mr.mergedAt());
            items.save(item);
            DevProject project = projectById.get(repo.getProjectId());
            if (repo.getSyncedAt() != null && project != null) {
                notifyMr(connection.userId(), project, repo, mr, previous, lanes.getOrDefault(repo.getId(), Map.of()).get(mr.id()));
            }
        }

        for (RemoteIssue issue : snapshot.issues()) {
            DevRepo repo = byExternal.get(issue.repoId());
            if (repo == null) continue;
            String key = repo.getId() + "|" + Kind.ISSUE + "|" + issue.number();
            seen.add(key);
            DevItem item = stored.getOrDefault(key, new DevItem(repo.getId(), connection.userId(), Kind.ISSUE, issue.number()));
            item.setBasics(issue.number(), issue.title(), "opened", issue.webUrl(), issue.author(), issue.updatedAt());
            item.setIssue(issue.labels(), issue.dueDate());
            DevProject project = projectById.get(repo.getProjectId());
            if (repo.isImportIssues() && !item.isTaskLinked() && project != null) {
                tasks.createPersonal(connection.userId(), "#" + issue.number() + " " + issue.title(), issue.webUrl(), issue.dueDate(),
                        project.getName(), project.getColor()).ifPresent(item::linkTask);
            }
            items.save(item);
        }

        for (RemotePipeline pipeline : snapshot.pipelines()) {
            DevRepo repo = byExternal.get(pipeline.repoId());
            if (repo == null) continue;
            String key = repo.getId() + "|" + Kind.PIPELINE + "|" + pipeline.repoId();
            seen.add(key);
            DevItem before = stored.get(key);
            String old = before == null ? null : before.getPipeline();
            String previousRun = before == null ? null : before.getNumber();
            DevItem item = before != null ? before : new DevItem(repo.getId(), connection.userId(), Kind.PIPELINE, pipeline.repoId());
            item.setBasics(pipeline.id(), "Pipeline de " + Objects.requireNonNullElse(pipeline.ref(), "?"),
                    Objects.requireNonNullElse(pipeline.status(), ""), pipeline.webUrl(), null, pipeline.updatedAt());
            item.setPipeline(pipeline.status(), pipeline.ref());
            items.save(item);
            DevProject project = projectById.get(repo.getProjectId());
            boolean newFailure = before != null && Pipelines.FAILED.equals(pipeline.status())
                    && (!Pipelines.FAILED.equals(old) || !Objects.equals(previousRun, pipeline.id()));
            if (repo.getSyncedAt() != null && project != null && newFailure) {
                notify(connection.userId(), NotificationType.DEV_PIPELINE_FAILED, project, repo,
                        Map.of("title", "branch " + Objects.requireNonNullElse(pipeline.ref(), ""),
                                "branch", Objects.requireNonNullElse(pipeline.ref(), "")));
            }
        }

        for (Map.Entry<String, DevItem> entry : stored.entrySet()) {
            if (seen.contains(entry.getKey())) continue;
            DevItem item = entry.getValue();
            if (item.getKind() == Kind.ISSUE && item.getTaskId() != null) {
                DevRepo repo = linked.stream().filter(r -> r.getId().equals(item.getRepoId())).findFirst().orElse(null);
                if (repo != null && closed.contains(repo.getExternalId() + "#" + item.getNumber())) {
                    UUID taskId = item.getTaskId();
                    items.delete(item);
                    items.flush();
                    tasks.completePersonal(connection.userId(), taskId);
                    continue;
                }
            }
            items.delete(item);
        }

        saveActivity(connection.userId(), linked, byExternal, days, from, today);
        linked.forEach(r -> repos.findById(r.getId()).ifPresent(fresh -> fresh.setSyncedAt(now)));
    }

    private void saveActivity(UUID userId, List<DevRepo> linked, Map<String, DevRepo> byExternal, List<RemoteActivity> days,
                              LocalDate from, LocalDate today) {
        Map<String, DevActivity> current = new HashMap<>();
        activity.findByRepoIdInAndDayGreaterThanEqual(linked.stream().map(DevRepo::getId).toList(), from)
                .forEach(a -> current.put(a.getRepoId() + "|" + a.getDay(), a));
        Set<String> touched = new HashSet<>();
        for (RemoteActivity day : days) {
            DevRepo repo = byExternal.get(day.repoId());
            if (repo == null || day.day().isBefore(from) || day.day().isAfter(today)) continue;
            String key = repo.getId() + "|" + day.day();
            touched.add(key);
            DevActivity row = current.computeIfAbsent(key, k -> new DevActivity(repo.getId(), userId, day.day()));
            row.set(day.commits(), day.mergeRequests(), day.merged(), day.reviews());
            activity.save(row);
        }
        if (!days.isEmpty()) {
            current.forEach((key, row) -> {
                if (!touched.contains(key)) activity.delete(row);
            });
        }
    }

    private void notifyMr(UUID userId, DevProject project, DevRepo repo, RemoteMr mr, Previous previous, String lane) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("title", mr.title());
        data.put("number", mr.number());
        if (mr.review() && !mr.mine()) {
            if (previous == null) {
                data.put("author", Objects.requireNonNullElse(mr.author(), ""));
                notify(userId, NotificationType.DEV_REVIEW_REQUESTED, project, repo, data);
            }
            return;
        }
        if (!mr.mine()) return;
        if ("merged".equals(mr.state()) && (previous == null ? mr.mergedAt() != null && repo.getSyncedAt() != null
                && mr.mergedAt().isAfter(repo.getSyncedAt()) : !"merged".equals(previous.state()))) {
            if (lane != null) data.put("lane", lane);
            notify(userId, NotificationType.DEV_MR_MERGED, project, repo, data);
            return;
        }
        if (previous == null || !"opened".equals(mr.state())) return;
        if (Pipelines.FAILED.equals(mr.pipeline()) && !Pipelines.FAILED.equals(previous.pipeline())) {
            notify(userId, NotificationType.DEV_PIPELINE_FAILED, project, repo, new LinkedHashMap<>(data));
        }
        if (mr.approved() && !previous.approved()) {
            notify(userId, NotificationType.DEV_MR_APPROVED, project, repo, new LinkedHashMap<>(data));
        }
        if (mr.comments() > previous.comments()) {
            Map<String, String> comments = new LinkedHashMap<>(data);
            comments.put("count", String.valueOf(mr.comments() - previous.comments()));
            notify(userId, NotificationType.DEV_MR_COMMENTED, project, repo, comments);
        }
    }

    private void notify(UUID userId, NotificationType type, DevProject project, DevRepo repo, Map<String, String> extra) {
        Map<String, String> data = new LinkedHashMap<>(extra);
        data.put("projectId", project.getId().toString());
        data.put("projectName", project.getName());
        data.put("repoName", repo.getFullName());
        notifications.notify(userId, type, null, null, data);
    }

    /** Evento de raia que o MR dispara agora (nulo = nenhum). */
    private static Event laneEvent(DevRepo repo, RemoteMr mr) {
        if ("merged".equals(mr.state()) && repo.getLabelMrMerged() != null && mr.mergedAt() != null
                && mr.mergedAt().isAfter(repo.getRulesSince())) {
            return Event.MERGED;
        }
        if ("opened".equals(mr.state()) && !mr.draft() && repo.getLabelMrOpened() != null && mr.createdAt() != null
                && mr.createdAt().isAfter(repo.getRulesSince())) {
            return Event.OPENED;
        }
        return null;
    }

    private record Previous(String state, String pipeline, boolean approved, int comments) {
    }
}
