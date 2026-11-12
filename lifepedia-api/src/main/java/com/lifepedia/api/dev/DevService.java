package com.lifepedia.api.dev;

import com.lifepedia.api.dev.DevDtos.AccountProblem;
import com.lifepedia.api.dev.DevDtos.ActivityResponse;
import com.lifepedia.api.dev.DevDtos.Counts;
import com.lifepedia.api.dev.DevDtos.DayActivity;
import com.lifepedia.api.dev.DevDtos.ItemResponse;
import com.lifepedia.api.dev.DevDtos.Link;
import com.lifepedia.api.dev.DevDtos.ProjectActivity;
import com.lifepedia.api.dev.DevDtos.ProjectDetail;
import com.lifepedia.api.dev.DevDtos.ProjectExport;
import com.lifepedia.api.dev.DevDtos.ProjectRequest;
import com.lifepedia.api.dev.DevDtos.ProjectSummary;
import com.lifepedia.api.dev.DevDtos.RepoOption;
import com.lifepedia.api.dev.DevDtos.RepoRequest;
import com.lifepedia.api.dev.DevDtos.RepoResponse;
import com.lifepedia.api.dev.DevDtos.RulesRequest;
import com.lifepedia.api.dev.DevDtos.Today;
import com.lifepedia.api.dev.DevItem.Kind;
import com.lifepedia.api.integration.Connection;
import com.lifepedia.api.integration.IntegrationDtos.EventResponse;
import com.lifepedia.api.integration.IntegrationException;
import com.lifepedia.api.integration.IntegrationLinks;
import com.lifepedia.api.integration.IntegrationLinks.LinkedAccount;
import com.lifepedia.api.integration.IntegrationProvider;
import com.lifepedia.api.integration.code.CodeHost.RemoteRepo;
import com.lifepedia.api.integration.code.Pipelines;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.RateLimiter;
import com.lifepedia.api.shared.ai.GeminiClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Projetos do Dev, repositórios ligados e regras de raia, e as telas de projeto, "Hoje no dev" e atividade. */
@Service
public class DevService {

    static final int MAX_PROJECTS = 30;
    static final int MAX_REPOS = 15;
    static final int MAX_WEEKS = 26;
    private static final TypeReference<List<Link>> LINKS = new TypeReference<>() {
    };
    private static final Comparator<ItemResponse> ITEM_ORDER = Comparator
            .comparing((ItemResponse i) -> i.kind().ordinal())
            .thenComparing(ItemResponse::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ItemResponse::id);

    private final DevProjectRepository projects;
    private final DevRepoRepository repos;
    private final DevItemRepository items;
    private final DevActivityRepository activity;
    private final IntegrationLinks integrations;
    private final GeminiClient gemini;
    private final RateLimiter rateLimiter;
    private final JsonMapper json;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DevService(DevProjectRepository projects, DevRepoRepository repos, DevItemRepository items, DevActivityRepository activity,
                      IntegrationLinks integrations, GeminiClient gemini, RateLimiter rateLimiter, JsonMapper json, Clock clock,
                      PlatformTransactionManager transactions) {
        this.projects = projects;
        this.repos = repos;
        this.items = items;
        this.activity = activity;
        this.integrations = integrations;
        this.gemini = gemini;
        this.rateLimiter = rateLimiter;
        this.json = json;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> projects(UUID userId) {
        List<DevProject> mine = projects.findByUserIdOrderByPositionAscCreatedAtAsc(userId);
        Context context = context(userId);
        return mine.stream().map(p -> summary(p, context)).toList();
    }

    @Transactional
    public ProjectSummary create(UUID userId, ProjectRequest request) {
        if (projects.countByUserId(userId) >= MAX_PROJECTS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_PROJECTS + " projetos.");
        }
        DevProject project = new DevProject(userId, projects.maxPosition(userId) + 1);
        apply(userId, project, request);
        projects.save(project);
        return summary(project, context(userId));
    }

    @Transactional
    public ProjectSummary update(UUID userId, UUID id, ProjectRequest request) {
        DevProject project = find(userId, id);
        apply(userId, project, request);
        return summary(project, context(userId));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        projects.delete(find(userId, id));
    }

    @Transactional
    public void reorder(UUID userId, List<UUID> ids) {
        Map<UUID, DevProject> mine = projects.findByUserIdOrderByPositionAscCreatedAtAsc(userId).stream()
                .collect(Collectors.toMap(DevProject::getId, Function.identity()));
        int position = 1;
        for (UUID id : ids) {
            DevProject project = mine.remove(id);
            if (project != null) project.setPosition(position++);
        }
        for (DevProject rest : mine.values()) rest.setPosition(position++);
    }

    @Transactional(readOnly = true)
    public ProjectDetail detail(UUID userId, UUID id) {
        DevProject project = find(userId, id);
        Context context = context(userId);
        List<UUID> repoIds = context.reposOf(project.getId()).stream().map(DevRepo::getId).toList();
        List<ItemResponse> list = context.items.stream().filter(i -> repoIds.contains(i.getRepoId()))
                .map(i -> item(i, context)).sorted(ITEM_ORDER).toList();
        List<EventResponse> events = List.of();
        if (project.getCalendarAccountId() != null) {
            LocalDate today = today();
            events = integrations.calendar(userId, today, today.plusDays(7)).stream()
                    .filter(e -> e.accountId().equals(project.getCalendarAccountId())).toList();
        }
        LocalDate from = today().minusWeeks(12).plusDays(1);
        List<DayActivity> days = days(activity.findByRepoIdInAndDayGreaterThanEqual(repoIds, from), from);
        return new ProjectDetail(summary(project, context), list, events, days);
    }

    /** Repositórios da conta para escolher (busca no serviço, no máximo 30 por minuto). */
    public List<RepoOption> repoOptions(UUID userId, UUID accountId, String query) {
        LinkedAccount account = integrations.account(userId, accountId);
        requireCodeHost(account);
        requireCall(userId);
        Set<String> linked = transaction.execute(status -> repos.findByAccountId(accountId).stream().map(DevRepo::getExternalId)
                .collect(Collectors.toSet()));
        try {
            Connection connection = integrations.connection(userId, accountId);
            return integrations.codeHost(account.provider()).repos(connection, query).stream()
                    .map(r -> new RepoOption(r.externalId(), r.fullName(), r.webUrl(), linked != null && linked.contains(r.externalId())))
                    .toList();
        } catch (IntegrationException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    public RepoResponse addRepo(UUID userId, UUID projectId, RepoRequest request) {
        transaction.execute(status -> find(userId, projectId));
        LinkedAccount account = integrations.account(userId, request.accountId());
        requireCodeHost(account);
        requireCall(userId);
        RemoteRepo remote;
        try {
            Connection connection = integrations.connection(userId, account.id());
            remote = integrations.codeHost(account.provider()).repo(connection, request.externalId().trim());
        } catch (IntegrationException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, e.kind() == IntegrationException.Kind.NOT_FOUND
                    ? "Repositório não encontrado com essa conta." : e.getMessage());
        }
        UUID id = transaction.execute(status -> {
            if (repos.countByProjectId(projectId) >= MAX_REPOS) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Limite de " + MAX_REPOS + " repositórios por projeto.");
            }
            if (repos.existsByProjectIdAndAccountIdAndExternalId(projectId, account.id(), remote.externalId())) {
                throw new BusinessException(HttpStatus.CONFLICT, "Esse repositório já está no projeto.");
            }
            DevRepo repo = new DevRepo(projectId, userId, account.id(), remote.externalId(), OffsetDateTime.now(clock));
            repo.setRemote(cut(remote.fullName(), 300), cut(remote.webUrl() == null ? "" : remote.webUrl(), 500),
                    remote.defaultBranch() == null ? null : cut(remote.defaultBranch(), 200));
            return repos.save(repo).getId();
        });
        integrations.syncNow(account.id());
        return transaction.execute(status -> repo(findRepo(userId, id), context(userId)));
    }

    @Transactional
    public RepoResponse updateRules(UUID userId, UUID repoId, RulesRequest request) {
        DevRepo repo = findRepo(userId, repoId);
        String opened = label(request.labelMrOpened());
        String merged = label(request.labelMrMerged());
        if (opened != null && opened.equalsIgnoreCase(merged)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Use etiquetas diferentes para MR aberto e MR aceito.");
        }
        String clear = request.labelsClear() == null ? null : request.labelsClear().stream().map(String::trim)
                .filter(s -> !s.isEmpty() && !s.contains(",")).distinct().collect(Collectors.joining(","));
        if (clear != null && (clear.isEmpty() || clear.length() > 400)) clear = clear.isEmpty() ? null : clear.substring(0, 400);
        repo.setRules(request.importIssues(), request.closeIssues(), opened, merged, clear, OffsetDateTime.now(clock));
        return repo(repo, context(userId));
    }

    @Transactional
    public void deleteRepo(UUID userId, UUID repoId) {
        repos.delete(findRepo(userId, repoId));
    }

    /** Etiquetas do repositório no serviço (para escolher as raias). */
    public List<String> labels(UUID userId, UUID repoId) {
        DevRepo repo = transaction.execute(status -> findRepo(userId, repoId));
        LinkedAccount account = integrations.account(userId, repo.getAccountId());
        requireCall(userId);
        try {
            return integrations.codeHost(account.provider()).labels(integrations.connection(userId, account.id()), repo.getExternalId())
                    .stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        } catch (IntegrationException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    /** Painel da manhã: reuniões de hoje, MRs esperando, revisões, pipelines quebradas e issues. */
    @Transactional(readOnly = true)
    public Today today(UUID userId) {
        Context context = context(userId);
        List<ItemResponse> all = context.items.stream().map(i -> item(i, context)).sorted(ITEM_ORDER).toList();
        List<ItemResponse> mine = all.stream().filter(i -> i.kind() == Kind.MR && i.mine() && "opened".equals(i.state())).toList();
        List<ItemResponse> reviews = all.stream().filter(i -> i.kind() == Kind.MR && i.review() && "opened".equals(i.state())).toList();
        List<ItemResponse> failing = all.stream().filter(i -> Pipelines.FAILED.equals(i.pipeline())
                && (i.kind() == Kind.PIPELINE || "opened".equals(i.state()))).toList();
        List<ItemResponse> issues = all.stream().filter(i -> i.kind() == Kind.ISSUE).toList();
        LocalDate today = today();
        List<EventResponse> events = integrations.calendar(userId, today, today).stream()
                .filter(e -> !e.end().isBefore(OffsetDateTime.now(clock).minusHours(1)) || e.allDay()).toList();
        List<AccountProblem> problems = integrations.accounts(userId).stream()
                .filter(a -> !a.active() && (a.provider().isCodeHost() || a.provider() == IntegrationProvider.GOOGLE))
                .map(a -> new AccountProblem(a.id(), a.provider(), a.nickname())).toList();
        return new Today(today, events, mine, reviews, failing, issues, problems, gemini.enabled(), context.projects.size());
    }

    /** Atividade de {@code weeks} semanas (até 26), por dia e por projeto. */
    @Transactional(readOnly = true)
    public ActivityResponse activity(UUID userId, int weeks) {
        int span = Math.max(1, Math.min(weeks, MAX_WEEKS));
        LocalDate from = today().minusWeeks(span).plusDays(1);
        Context context = context(userId);
        List<DevActivity> rows = activity.findByUserIdAndDayGreaterThanEqualOrderByDayAsc(userId, from);
        Map<UUID, int[]> byProject = new LinkedHashMap<>();
        for (DevActivity row : rows) {
            DevRepo repo = context.repoById.get(row.getRepoId());
            if (repo == null) continue;
            int[] total = byProject.computeIfAbsent(repo.getProjectId(), k -> new int[4]);
            total[0] += row.getCommits();
            total[1] += row.getMergeRequests();
            total[2] += row.getMerged();
            total[3] += row.getReviews();
        }
        List<ProjectActivity> perProject = new ArrayList<>();
        for (DevProject project : context.projects) {
            int[] t = byProject.getOrDefault(project.getId(), new int[4]);
            perProject.add(new ProjectActivity(project.getId(), project.getName(), project.getColor(), t[0], t[1], t[2], t[3]));
        }
        return new ActivityResponse(days(rows, from), perProject);
    }

    /** Dados para o resumo do daily (IA): atividade de ontem, MRs, revisões, issues e reuniões de hoje. */
    @Transactional(readOnly = true)
    public Map<String, Object> dailyFacts(UUID userId) {
        Context context = context(userId);
        LocalDate today = today();
        LocalDate yesterday = previousWorkday(today);
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("hoje", today.toString());
        facts.put("diaAnterior", yesterday.toString());
        List<Map<String, Object>> worked = new ArrayList<>();
        for (DevActivity row : activity.findByUserIdAndDayGreaterThanEqualOrderByDayAsc(userId, yesterday)) {
            DevRepo repo = context.repoById.get(row.getRepoId());
            if (repo == null || !row.getDay().equals(yesterday)) continue;
            worked.add(Map.of("repositorio", repo.getFullName(), "commits", row.getCommits(), "mrsAbertos", row.getMergeRequests(),
                    "mrsAceitos", row.getMerged(), "revisoes", row.getReviews()));
        }
        facts.put("atividadeDiaAnterior", worked);
        List<Map<String, Object>> mrs = new ArrayList<>();
        for (DevItem item : context.items) {
            if (item.getKind() != Kind.MR) continue;
            DevRepo repo = context.repoById.get(item.getRepoId());
            Map<String, Object> mr = new LinkedHashMap<>();
            mr.put("titulo", item.getTitle());
            mr.put("repositorio", repo == null ? "" : repo.getFullName());
            mr.put("meu", item.isMine());
            mr.put("revisaoPedida", item.isReview());
            mr.put("estado", item.getState());
            mr.put("rascunho", item.isDraft());
            mr.put("pipeline", item.getPipeline() == null ? "" : item.getPipeline());
            mr.put("aprovado", item.isApproved());
            mr.put("aceitoEm", item.getMergedAt() == null ? ""
                    : item.getMergedAt().atZoneSameInstant(clock.getZone()).toLocalDate().toString());
            mrs.add(mr);
        }
        facts.put("mergeRequests", mrs);
        facts.put("issues", context.items.stream().filter(i -> i.getKind() == Kind.ISSUE)
                .map(i -> Map.of("titulo", i.getTitle(), "etiquetas", i.labelList())).toList());
        facts.put("pipelinesQuebradas", context.items.stream()
                .filter(i -> i.getKind() == Kind.PIPELINE && Pipelines.FAILED.equals(i.getPipeline()))
                .map(i -> context.repoById.get(i.getRepoId()).getFullName()).toList());
        facts.put("reunioesHoje", integrations.calendar(userId, today, today).stream()
                .map(e -> Map.of("titulo", e.title(), "inicio", e.allDay() ? "dia todo"
                        : e.start().atZoneSameInstant(clock.getZone()).toLocalTime().toString())).toList());
        return facts;
    }

    @Transactional(readOnly = true)
    public List<ProjectExport> exportData(UUID userId) {
        Context context = context(userId);
        return context.projects.stream().map(p -> new ProjectExport(p.getName(), p.getDescription(), links(p),
                context.reposOf(p.getId()).stream().map(DevRepo::getFullName).toList())).toList();
    }

    private void apply(UUID userId, DevProject project, ProjectRequest request) {
        UUID calendar = request.calendarAccountId();
        if (calendar != null) {
            LinkedAccount account = integrations.find(userId, calendar)
                    .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "Conta do Google inválida."));
            if (account.provider() != IntegrationProvider.GOOGLE) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha uma conta do Google Agenda.");
            }
        }
        List<Link> links = request.links() == null ? List.of() : request.links().stream()
                .map(l -> new Link(l.label().trim(), l.url().trim(), l.kind())).toList();
        String description = request.description() == null || request.description().isBlank() ? null : request.description().trim();
        project.set(request.name().trim(), request.color().toLowerCase(Locale.ROOT), description, json.writeValueAsString(links), calendar);
    }

    private Context context(UUID userId) {
        List<DevProject> mine = projects.findByUserIdOrderByPositionAscCreatedAtAsc(userId);
        List<DevRepo> repoList = repos.findByUserIdOrderByCreatedAtAsc(userId);
        Map<UUID, LinkedAccount> accounts = integrations.accounts(userId).stream()
                .collect(Collectors.toMap(LinkedAccount::id, Function.identity()));
        return new Context(mine, repoList, items.findByUserId(userId), accounts);
    }

    private ProjectSummary summary(DevProject project, Context context) {
        List<DevRepo> list = context.reposOf(project.getId());
        Set<UUID> ids = list.stream().map(DevRepo::getId).collect(Collectors.toSet());
        int mine = 0;
        int reviews = 0;
        int issues = 0;
        int failing = 0;
        for (DevItem item : context.items) {
            if (!ids.contains(item.getRepoId())) continue;
            boolean open = "opened".equals(item.getState());
            if (item.getKind() == Kind.MR && item.isMine() && open) mine++;
            if (item.getKind() == Kind.MR && item.isReview() && open) reviews++;
            if (item.getKind() == Kind.ISSUE) issues++;
            if (Pipelines.FAILED.equals(item.getPipeline()) && (item.getKind() == Kind.PIPELINE || open)) failing++;
        }
        return new ProjectSummary(project.getId(), project.getName(), project.getColor(), project.getDescription(), links(project),
                project.getCalendarAccountId(), list.stream().map(r -> repo(r, context)).toList(),
                new Counts(mine, reviews, issues, failing));
    }

    private RepoResponse repo(DevRepo repo, Context context) {
        LinkedAccount account = context.accounts.get(repo.getAccountId());
        return new RepoResponse(repo.getId(), repo.getAccountId(), account == null ? "" : account.nickname(),
                account == null ? "#888888" : account.color(), account == null ? null : account.provider(),
                account != null && account.active(), account != null && account.canWrite(), repo.getFullName(), repo.getWebUrl(),
                repo.getDefaultBranch(), repo.isImportIssues(), repo.isCloseIssues(), repo.getLabelMrOpened(), repo.getLabelMrMerged(),
                repo.clearList(), repo.getSyncedAt());
    }

    private ItemResponse item(DevItem item, Context context) {
        DevRepo repo = context.repoById.get(item.getRepoId());
        DevProject project = repo == null ? null : context.projectById.get(repo.getProjectId());
        List<String> linked = item.getLinkedIssues() == null || item.getLinkedIssues().isBlank() ? List.of()
                : Arrays.asList(item.getLinkedIssues().split(","));
        return new ItemResponse(item.getId(), project == null ? null : project.getId(), project == null ? "" : project.getName(),
                project == null ? "#888888" : project.getColor(), item.getRepoId(), repo == null ? "" : repo.getFullName(),
                item.getKind(), item.getNumber(), item.getTitle(), item.getState(), item.getWebUrl(), item.getAuthor(), item.isMine(),
                item.isReview(), item.isDraft(), item.getPipeline(), item.getBranch(), item.labelList(), item.getComments(),
                item.isApproved(), linked, item.getDueDate(), item.getTaskId(), item.getRemoteUpdatedAt(), item.getMergedAt());
    }

    private List<Link> links(DevProject project) {
        try {
            return json.readValue(project.getLinks(), LINKS);
        } catch (JacksonException e) {
            return List.of();
        }
    }

    private void requireCall(UUID userId) {
        if (!rateLimiter.tryAcquire("dev-remote:" + userId, 30, Duration.ofMinutes(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitas buscas seguidas. Espere um pouco.");
        }
    }

    private DevProject find(UUID userId, UUID id) {
        return projects.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Projeto não encontrado."));
    }

    private DevRepo findRepo(UUID userId, UUID id) {
        return repos.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Repositório não encontrado."));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static void requireCodeHost(LinkedAccount account) {
        if (!account.provider().isCodeHost()) throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha uma conta do GitLab ou GitHub.");
    }

    /** Dias de {@code from} até hoje somando os repositórios (dia sem nada vem zerado). */
    private List<DayActivity> days(List<DevActivity> rows, LocalDate from) {
        Map<LocalDate, int[]> byDay = new HashMap<>();
        for (DevActivity row : rows) {
            int[] t = byDay.computeIfAbsent(row.getDay(), k -> new int[4]);
            t[0] += row.getCommits();
            t[1] += row.getMergeRequests();
            t[2] += row.getMerged();
            t[3] += row.getReviews();
        }
        List<DayActivity> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today()); d = d.plusDays(1)) {
            int[] t = byDay.getOrDefault(d, new int[4]);
            days.add(new DayActivity(d, t[0], t[1], t[2], t[3]));
        }
        return days;
    }

    private static LocalDate previousWorkday(LocalDate today) {
        LocalDate day = today.minusDays(1);
        while (day.getDayOfWeek().getValue() >= 6) day = day.minusDays(1);
        return day;
    }

    private static String label(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Dados da pessoa carregados uma vez por requisição. */
    private static final class Context {
        private final List<DevProject> projects;
        private final List<DevItem> items;
        private final Map<UUID, LinkedAccount> accounts;
        private final Map<UUID, DevProject> projectById;
        private final Map<UUID, DevRepo> repoById;
        private final Map<UUID, List<DevRepo>> reposByProject;

        Context(List<DevProject> projects, List<DevRepo> repos, List<DevItem> items, Map<UUID, LinkedAccount> accounts) {
            this.projects = projects;
            this.items = items;
            this.accounts = accounts;
            this.projectById = projects.stream().collect(Collectors.toMap(DevProject::getId, Function.identity()));
            this.repoById = repos.stream().collect(Collectors.toMap(DevRepo::getId, Function.identity()));
            this.reposByProject = repos.stream().collect(Collectors.groupingBy(DevRepo::getProjectId));
        }

        List<DevRepo> reposOf(UUID projectId) {
            return reposByProject.getOrDefault(projectId, List.of());
        }
    }
}
