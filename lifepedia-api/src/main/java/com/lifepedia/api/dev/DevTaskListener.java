package com.lifepedia.api.dev;

import com.lifepedia.api.dev.DevItem.Kind;
import com.lifepedia.api.integration.IntegrationException;
import com.lifepedia.api.integration.IntegrationLinks;
import com.lifepedia.api.integration.IntegrationLinks.LinkedAccount;
import com.lifepedia.api.integration.code.CodeHost.IssueRef;
import com.lifepedia.api.task.TaskDoneEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/** Concluiu no LifePedia a tarefa de uma issue: fecha a issue lá se o repositório pede (depois do commit, fora da requisição). */
@Component
class DevTaskListener {

    private static final Logger log = LoggerFactory.getLogger(DevTaskListener.class);

    private final DevItemRepository items;
    private final DevRepoRepository repos;
    private final IntegrationLinks integrations;
    private final TransactionTemplate transaction;

    DevTaskListener(DevItemRepository items, DevRepoRepository repos, IntegrationLinks integrations,
                    PlatformTransactionManager transactions) {
        this.items = items;
        this.repos = repos;
        this.integrations = integrations;
        this.transaction = new TransactionTemplate(transactions);
    }

    @TransactionalEventListener
    void onTaskDone(TaskDoneEvent event) {
        Thread.ofVirtual().start(() -> close(event));
    }

    /** Fecha a issue ligada à tarefa (o teste chama direto, sem esperar o thread). */
    void close(TaskDoneEvent event) {
        Target target = transaction.execute(status -> items.findFirstByTaskIdAndKind(event.taskId(), Kind.ISSUE)
                .flatMap(item -> repos.findById(item.getRepoId())
                        .filter(repo -> repo.isCloseIssues() && repo.getUserId().equals(event.userId()))
                        .map(repo -> new Target(repo.getAccountId(), new IssueRef(repo.getExternalId(), item.getNumber()))))
                .orElse(null));
        if (target == null) return;
        try {
            LinkedAccount account = integrations.account(event.userId(), target.accountId());
            if (!account.canWrite()) return;
            integrations.codeHost(account.provider()).closeIssue(integrations.connection(event.userId(), account.id()), target.issue());
            integrations.syncNow(account.id());
        } catch (IntegrationException | RuntimeException e) {
            log.info("Issue da tarefa {} não foi fechada: {}", event.taskId(), e.getClass().getSimpleName());
        }
    }

    private record Target(UUID accountId, IssueRef issue) {
    }
}
