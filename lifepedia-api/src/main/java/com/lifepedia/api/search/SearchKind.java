package com.lifepedia.api.search;

import com.lifepedia.api.user.AppModule;

/**
 * Tipo de resultado da busca geral.
 *
 * @param dated   aceita filtro de período
 * @param valued  aceita filtro de valor
 * @param finance aceita filtro de entrada/saída e categoria
 * @param group   aparece na busca dentro de um grupo
 */
public enum SearchKind {
    TRANSACTION(AppModule.FINANCE, true, true, true, false),
    RECURRING(AppModule.FINANCE, false, true, true, false),
    CATEGORY(AppModule.FINANCE, false, false, false, false),
    CARD(AppModule.FINANCE, false, false, false, false),
    ACCOUNT(AppModule.FINANCE, false, false, false, false),
    GROUP_TRANSACTION(AppModule.FINANCE, true, true, true, true),
    GROUP_RECURRING(AppModule.FINANCE, false, true, true, true),
    FINANCING(AppModule.FINANCING, false, true, false, true),
    TASK(AppModule.TASKS, true, false, false, true),
    EVENT(AppModule.AGENDA, true, false, false, false),
    COMMITMENT(AppModule.ATTENDANCE, false, false, false, false),
    NOTE(AppModule.NOTES, true, false, false, true),
    DELIVERY(AppModule.DELIVERIES, true, false, false, true),
    VEHICLE(AppModule.VEHICLES, false, false, false, false),
    VEHICLE_EXPENSE(AppModule.VEHICLES, true, true, false, false),
    VEHICLE_SERVICE(AppModule.VEHICLES, true, false, false, false),
    GROUP(AppModule.GROUPS, false, false, false, false),
    PLAN_ITEM(AppModule.GROUPS, true, true, false, true),
    MESSAGE(AppModule.GROUPS, true, false, false, true),
    FRIEND(AppModule.FRIENDS, false, false, false, false),
    DEV_PROJECT(AppModule.DEV, false, false, false, false),
    DEV_ITEM(AppModule.DEV, false, false, false, false),
    ARCH_PROJECT(AppModule.ARCHITECTURE, false, false, false, false),
    PRICE_WATCH(AppModule.PRICES, false, true, false, true);

    private final AppModule module;
    private final boolean dated;
    private final boolean valued;
    private final boolean finance;
    private final boolean group;

    SearchKind(AppModule module, boolean dated, boolean valued, boolean finance, boolean group) {
        this.module = module;
        this.dated = dated;
        this.valued = valued;
        this.finance = finance;
        this.group = group;
    }

    public AppModule module() {
        return module;
    }

    public boolean dated() {
        return dated;
    }

    public boolean valued() {
        return valued;
    }

    public boolean finance() {
        return finance;
    }

    public boolean group() {
        return group;
    }
}
