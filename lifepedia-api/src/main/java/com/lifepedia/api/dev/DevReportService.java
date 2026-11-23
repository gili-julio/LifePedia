package com.lifepedia.api.dev;

import com.lifepedia.api.dev.DevDtos.ReportResponse;
import com.lifepedia.api.dev.DevDtos.ReportSuggestion;
import com.lifepedia.api.dev.DevItem.Kind;
import com.lifepedia.api.integration.IntegrationLinks;
import com.lifepedia.api.integration.IntegrationLinks.LinkedAccount;
import com.lifepedia.api.integration.IntegrationProvider;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Anotações do dia em cada projeto do Dev e o rascunho montado com o que o GitLab/GitHub registrou. */
@Service
public class DevReportService {

    private final DevProjectRepository projects;
    private final DevRepoRepository repos;
    private final DevItemRepository items;
    private final DevActivityRepository activity;
    private final DevReportRepository reports;
    private final IntegrationLinks integrations;
    private final Clock clock;

    public DevReportService(DevProjectRepository projects, DevRepoRepository repos, DevItemRepository items,
                            DevActivityRepository activity, DevReportRepository reports, IntegrationLinks integrations, Clock clock) {
        this.projects = projects;
        this.repos = repos;
        this.items = items;
        this.activity = activity;
        this.reports = reports;
        this.integrations = integrations;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> list(UUID userId, UUID projectId, YearMonth month) {
        requireProject(userId, projectId);
        return reports.findByProjectIdAndDayBetweenOrderByDayDesc(projectId, month.atDay(1), month.atEndOfMonth()).stream()
                .map(DevReportService::response).toList();
    }

    @Transactional
    public ReportResponse save(UUID userId, UUID projectId, LocalDate day, String text) {
        requireProject(userId, projectId);
        requirePast(day);
        DevReport report = reports.findByProjectIdAndDay(projectId, day).orElseGet(() -> new DevReport(projectId, userId, day));
        report.setText(text.strip());
        return response(reports.saveAndFlush(report));
    }

    @Transactional
    public void delete(UUID userId, UUID projectId, LocalDate day) {
        requireProject(userId, projectId);
        reports.findByProjectIdAndDay(projectId, day).ifPresent(reports::delete);
    }

    /** MRs abertos, aceitos e mexidos no dia, issues que mudaram de raia, commits e revisões, em tópicos. */
    @Transactional(readOnly = true)
    public ReportSuggestion suggestion(UUID userId, UUID projectId, LocalDate day) {
        requireProject(userId, projectId);
        requirePast(day);
        Map<UUID, DevRepo> repoById = repos.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(r -> r.getProjectId().equals(projectId)).collect(Collectors.toMap(DevRepo::getId, Function.identity()));
        Map<UUID, IntegrationProvider> providers = integrations.accounts(userId).stream()
                .collect(Collectors.toMap(LinkedAccount::id, LinkedAccount::provider));
        List<DevItem> found = items.findByRepoIdIn(repoById.keySet());
        List<String> lines = new ArrayList<>();
        for (DevItem item : found) {
            if (item.getKind() != Kind.MR || !item.isMine()) continue;
            DevRepo repo = repoById.get(item.getRepoId());
            String mr = (providers.get(repo.getAccountId()) == IntegrationProvider.GITHUB ? "o PR #" : "o MR !") + item.getNumber() + " "
                    + item.getTitle() + " (" + repo.getFullName() + ")";
            boolean opened = on(item.getOpenedAt(), day);
            boolean merged = on(item.getMergedAt(), day);
            if (opened) lines.add("Abri " + mr);
            if (merged) lines.add("Foi aceito " + mr);
            if (!opened && !merged && "opened".equals(item.getState()) && on(item.getRemoteUpdatedAt(), day)) lines.add("Trabalhei n" + mr);
        }
        for (DevItem item : found) {
            if (item.getKind() != Kind.ISSUE || !on(item.getLaneSince(), day)) continue;
            lines.add("Issue #" + item.getNumber() + " " + item.getTitle() + " foi para " + item.getLane()
                    + " (" + repoById.get(item.getRepoId()).getFullName() + ")");
        }
        for (DevActivity row : activity.findByRepoIdInAndDayGreaterThanEqual(repoById.keySet(), day)) {
            if (!row.getDay().equals(day)) continue;
            String repo = repoById.get(row.getRepoId()).getFullName();
            if (row.getCommits() > 0) lines.add(row.getCommits() + (row.getCommits() == 1 ? " commit em " : " commits em ") + repo);
            if (row.getReviews() > 0) {
                lines.add(row.getReviews() + (row.getReviews() == 1 ? " revisão" : " revisões") + " de MR em " + repo);
            }
        }
        return new ReportSuggestion(lines.stream().map(l -> "- " + l).collect(Collectors.joining("\n")));
    }

    private boolean on(OffsetDateTime time, LocalDate day) {
        return time != null && time.atZoneSameInstant(clock.getZone()).toLocalDate().equals(day);
    }

    private void requirePast(LocalDate day) {
        if (day.isAfter(LocalDate.now(clock))) throw new BusinessException(HttpStatus.BAD_REQUEST, "Escolha hoje ou um dia que já passou.");
    }

    private void requireProject(UUID userId, UUID projectId) {
        if (projects.findByIdAndUserId(projectId, userId).isEmpty()) throw new NotFoundException("Projeto não encontrado.");
    }

    private static ReportResponse response(DevReport report) {
        return new ReportResponse(report.getDay(), report.getText(), report.getUpdatedAt());
    }
}
