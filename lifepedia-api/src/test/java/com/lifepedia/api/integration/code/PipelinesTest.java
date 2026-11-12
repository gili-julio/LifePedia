package com.lifepedia.api.integration.code;

import com.lifepedia.api.integration.code.CodeHost.IssueRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PipelinesTest {

    @Test
    void normalizesGitLabAndGitHub() {
        assertThat(Pipelines.normalize("success", null)).isEqualTo(Pipelines.SUCCESS);
        assertThat(Pipelines.normalize("failed", null)).isEqualTo(Pipelines.FAILED);
        assertThat(Pipelines.normalize("waiting_for_resource", null)).isEqualTo(Pipelines.PENDING);
        assertThat(Pipelines.normalize("completed", "failure")).isEqualTo(Pipelines.FAILED);
        assertThat(Pipelines.normalize("completed", "cancelled")).isEqualTo(Pipelines.CANCELED);
        assertThat(Pipelines.normalize("in_progress", null)).isEqualTo(Pipelines.RUNNING);
        assertThat(Pipelines.normalize(null, null)).isNull();
        assertThat(Pipelines.worst(List.of(Pipelines.SUCCESS, Pipelines.RUNNING, Pipelines.FAILED))).isEqualTo(Pipelines.FAILED);
        assertThat(Pipelines.worst(List.of())).isNull();
    }

    @Test
    void linkedIssuesFromDescriptionAndBranch() {
        assertThat(Pipelines.linkedIssues("12", "Corrige login.\nCloses #45, fixes #46 e resolve #7", "45-login"))
                .containsExactly(new IssueRef("12", "45"), new IssueRef("12", "46"), new IssueRef("12", "7"));
        assertThat(Pipelines.linkedIssues("a/b", null, "feature/88-tela")).containsExactly(new IssueRef("a/b", "88"));
        assertThat(Pipelines.linkedIssues("a/b", "Relacionado a #9", "main")).isEmpty();
        assertThat(IssueRef.parse("grupo/repo#12")).isEqualTo(new IssueRef("grupo/repo", "12"));
    }
}
