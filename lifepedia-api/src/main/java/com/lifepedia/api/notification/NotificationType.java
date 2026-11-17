package com.lifepedia.api.notification;

/** Tipos de notificação. Um tipo novo precisa do texto correspondente no front (features/notifications). */
public enum NotificationType {
    /** data: actorName, actorUsername, groupName, inviteId */
    GROUP_INVITE,
    /** data: actorName, actorUsername, groupName, promoted ("true" para quem virou admin) */
    MEMBER_ACCOUNT_DELETED,
    /** data: actorName, actorUsername, groupName, taskId, taskTitle, date (se houver) */
    TASK_ASSIGNED,
    /** Vez no rodízio. data: actorName, actorUsername, groupName, taskId, taskTitle, date */
    TASK_TURN,
    /** Alguém concluiu uma tarefa da pessoa. data: actorName, actorUsername, groupName, taskId, taskTitle, date */
    TASK_COMPLETED,
    /** data: actorName, actorUsername, groupName, taskId, taskTitle, preview */
    TASK_COMMENT,
    /** Ocorrência que ficou atrasada (aviso diário). data: groupName, taskId, taskTitle, date */
    TASK_OVERDUE,
    /** Lembrete (pessoal ou de grupo). data: taskId, taskTitle, date (dia mostrado), time, minutes, groupName (grupo) */
    TASK_REMINDER,
    /** data: actorName, actorUsername, requestId */
    FRIEND_REQUEST,
    /** Pedido aceito. data: actorName, actorUsername */
    FRIEND_ACCEPTED,
    /** Parcela de financiamento perto do vencimento. data: financingId, financingName, amount (parte da pessoa), date, groupName (grupo) */
    FINANCING_DUE,
    /** Gasto do bem perto do vencimento. data: financingId, financingName, costName, amount, date, groupName (grupo) */
    FINANCING_COST_DUE,
    /** Leitura dos documentos pronta para revisar. data: draftId, items */
    IMPORT_READY,
    /** Leitura dos documentos falhou. data: draftId */
    IMPORT_FAILED,
    /** Mensagem da administração para todos (ou só admins). data: title, body */
    SYSTEM_MESSAGE,
    /** Projeto de arquitetura compartilhado. data: actorName, actorUsername, projectId, projectName, role, groupName (grupo) */
    ARCHITECTURE_SHARED,
    /** Comentário na maquete. data: actorName, actorUsername, projectId, projectName, preview */
    ARCHITECTURE_COMMENT,
    /** O serviço recusou o token da conta externa. data: provider, nickname, accountId */
    INTEGRATION_FAILED,
    /** Token pessoal perto de vencer. data: provider, nickname, accountId, date */
    INTEGRATION_EXPIRING,
    /** Pipeline falhou (MR da pessoa ou branch principal). data: projectId, projectName, repoName, title, number (MR), branch */
    DEV_PIPELINE_FAILED,
    /** MR da pessoa aprovado. data: projectId, projectName, repoName, title, number */
    DEV_MR_APPROVED,
    /** MR da pessoa aceito. data: projectId, projectName, repoName, title, number, lane (etiqueta aplicada, se houver) */
    DEV_MR_MERGED,
    /** Comentários novos no MR da pessoa. data: projectId, projectName, repoName, title, number, count */
    DEV_MR_COMMENTED,
    /** Pediram a revisão da pessoa. data: projectId, projectName, repoName, title, number, author */
    DEV_REVIEW_REQUESTED,
    /**
     * Novidade numa entrega (dono e membros do grupo dela). data: deliveryId, name, status (OUT_FOR_DELIVERY, PICKUP,
     * DELIVERED, FAILED, EXCEPTION, EXPIRED, TAX = aguardando pagamento, STALLED = parada), text (último evento), days
     */
    DELIVERY_UPDATE,
    /** Veículo compartilhado. data: actorName, actorUsername, vehicleId, vehicleName, role, groupName (grupo) */
    VEHICLE_SHARED,
    /**
     * Item de manutenção, revisão ou documento perto de vencer ou vencido (dono e quem lança). data: vehicleId,
     * vehicleName, kind (ITEM, REVISION, DOCUMENT), name, stage (SOON, DUE), date (data esperada), kmLeft
     */
    VEHICLE_REMINDER,
    /**
     * Fatura do cartão: fechando em até 3 dias (CLOSING), fechada vencendo em até 3 dias (DUE) ou meta da fatura em 80% e
     * 100% (BUDGET). data: cardId, cardName, kind, amount, date (fechamento ou vencimento), percent (só BUDGET)
     */
    CARD_REMINDER
}
