package com.lifepedia.api.finance.wallet;

/** Situação de uma fatura: ainda não começou, aberta, fechada esperando o vencimento, paga ou vencida sem pagamento. */
public enum InvoiceStatus {
    FUTURE,
    OPEN,
    CLOSED,
    PAID,
    OVERDUE
}
