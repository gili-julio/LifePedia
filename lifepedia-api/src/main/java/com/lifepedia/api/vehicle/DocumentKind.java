package com.lifepedia.api.vehicle;

/** Documento com vencimento; {@code expense} = tipo do gasto lançado ao renovar. */
public enum DocumentKind {
    IPVA(ExpenseKind.TAX),
    LICENSING(ExpenseKind.LICENSING),
    INSURANCE(ExpenseKind.INSURANCE),
    CNH(ExpenseKind.OTHER),
    INSPECTION(ExpenseKind.SERVICE),
    OTHER(ExpenseKind.OTHER);

    private final ExpenseKind expense;

    DocumentKind(ExpenseKind expense) {
        this.expense = expense;
    }

    public ExpenseKind expense() {
        return expense;
    }
}
