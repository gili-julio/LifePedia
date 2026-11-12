package com.lifepedia.api.integration.code;

import com.lifepedia.api.integration.Connection;
import com.lifepedia.api.integration.IntegrationException;
import com.lifepedia.api.integration.IntegrationProvider;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

/** GitLab ou GitHub: o que o módulo Dev e a central de integrações pedem a cada um. */
public interface CodeHost {

    IntegrationProvider provider();

    /** Confere o token e diz de quem é, até quando vale e se pode escrever (mover issue). */
    Identity identify(String baseUrl, String token) throws IntegrationException;

    /** Repositórios que a pessoa acessa, filtrados por {@code query} (até 50). */
    List<RemoteRepo> repos(Connection connection, String query) throws IntegrationException;

    RemoteRepo repo(Connection connection, String externalId) throws IntegrationException;

    /** Etiquetas do repositório (para escolher as raias). */
    List<String> labels(Connection connection, String repoId) throws IntegrationException;

    /** MRs da pessoa (abertos e aceitos desde {@code mergedSince}), para revisar, issues atribuídas e pipelines. */
    Snapshot snapshot(Connection connection, Collection<RemoteRepo> repos, OffsetDateTime mergedSince) throws IntegrationException;

    /** Issue ainda aberta? */
    boolean issueOpen(Connection connection, IssueRef issue) throws IntegrationException;

    void relabel(Connection connection, IssueRef issue, List<String> add, List<String> remove) throws IntegrationException;

    void closeIssue(Connection connection, IssueRef issue) throws IntegrationException;

    /** Commits, MRs e revisões da pessoa por dia (no fuso {@code zone}) desde {@code since}. */
    List<RemoteActivity> activity(Connection connection, Collection<String> repoIds, LocalDate since, ZoneId zone)
            throws IntegrationException;

    record Identity(String externalId, String login, LocalDate tokenExpiresAt, boolean canWrite) {
    }

    record RemoteRepo(String externalId, String fullName, String webUrl, String defaultBranch) {
    }

    /** Issue num repositório; {@code repoId} = id do projeto no GitLab ou "dono/nome" no GitHub. */
    record IssueRef(String repoId, String number) {

        public static IssueRef parse(String value) {
            int hash = value.lastIndexOf('#');
            return hash <= 0 ? null : new IssueRef(value.substring(0, hash), value.substring(hash + 1));
        }

        @Override
        public String toString() {
            return repoId + "#" + number;
        }
    }

    /**
     * @param state    {@code opened}, {@code merged} ou {@code closed}
     * @param pipeline status normalizado ({@link Pipelines}) ou nulo
     * @param issues   issues que o MR fecha (descrição ou número no começo do branch)
     */
    record RemoteMr(String repoId, String id, String number, String title, String state, String webUrl, String author,
                    boolean mine, boolean review, boolean draft, String branch, String pipeline, boolean approved,
                    int comments, List<IssueRef> issues, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                    OffsetDateTime mergedAt) {
    }

    record RemoteIssue(String repoId, String number, String title, String webUrl, String author, List<String> labels,
                       LocalDate dueDate, OffsetDateTime updatedAt) {
    }

    record RemotePipeline(String repoId, String id, String status, String ref, String webUrl, OffsetDateTime updatedAt) {
    }

    record Snapshot(List<RemoteMr> mergeRequests, List<RemoteIssue> issues, List<RemotePipeline> pipelines) {
    }

    record RemoteActivity(String repoId, LocalDate day, int commits, int mergeRequests, int merged, int reviews) {
    }
}
