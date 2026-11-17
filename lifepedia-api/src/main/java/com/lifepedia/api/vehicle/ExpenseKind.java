package com.lifepedia.api.vehicle;

/** Tipo de gasto; {@code group} junta os tipos nos gráficos. */
public enum ExpenseKind {
    FUEL(Group.FUEL),
    SERVICE(Group.MAINTENANCE),
    PART(Group.MAINTENANCE),
    TAX(Group.DOCUMENTS),
    LICENSING(Group.DOCUMENTS),
    INSURANCE(Group.DOCUMENTS),
    FINE(Group.DOCUMENTS),
    TOLL(Group.OTHER),
    WASH(Group.OTHER),
    OTHER(Group.OTHER);

    public enum Group {
        FUEL, MAINTENANCE, DOCUMENTS, OTHER
    }

    private final Group group;

    ExpenseKind(Group group) {
        this.group = group;
    }

    public Group group() {
        return group;
    }
}
