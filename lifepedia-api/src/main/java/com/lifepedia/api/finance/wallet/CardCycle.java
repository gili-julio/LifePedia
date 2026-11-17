package com.lifepedia.api.finance.wallet;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Ciclo de um cartão. A fatura é o mês do vencimento; fecha no último dia de fechamento antes do vencimento e a compra
 * feita no dia do fechamento ou depois já vai para a seguinte.
 */
public record CardCycle(int closingDay, int dueDay) {

    public LocalDate dueOn(YearMonth month) {
        return at(month, dueDay);
    }

    public LocalDate closesOn(YearMonth month) {
        LocalDate due = dueOn(month);
        LocalDate closing = at(month, closingDay);
        return closing.isBefore(due) ? closing : at(month.minusMonths(1), closingDay);
    }

    /** Primeiro dia de compras da fatura (o fechamento da anterior). */
    public LocalDate opensOn(YearMonth month) {
        return closesOn(month.minusMonths(1));
    }

    /** Fatura em que cai uma compra feita na data. */
    public YearMonth invoiceOf(LocalDate date) {
        YearMonth month = YearMonth.from(date);
        while (!date.isBefore(closesOn(month))) month = month.plusMonths(1);
        return month;
    }

    /** Fatura da parcela: a da compra mais as parcelas anteriores. */
    public YearMonth invoiceOf(LocalDate date, int installment) {
        return invoiceOf(date).plusMonths(Math.max(0, installment - 1L));
    }

    /** Situação da fatura hoje, sem contar pagamento. */
    public InvoiceStatus status(YearMonth month, LocalDate today) {
        if (today.isBefore(opensOn(month))) return InvoiceStatus.FUTURE;
        if (today.isBefore(closesOn(month))) return InvoiceStatus.OPEN;
        return today.isAfter(dueOn(month)) ? InvoiceStatus.OVERDUE : InvoiceStatus.CLOSED;
    }

    private static LocalDate at(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }
}
