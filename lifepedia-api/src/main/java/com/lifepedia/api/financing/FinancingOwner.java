package com.lifepedia.api.financing;

import java.util.UUID;

/** Dono do financiamento ou da simulação: um usuário (pessoal) ou um grupo; o outro campo é nulo. */
public record FinancingOwner(UUID userId, UUID groupId) {

    public static FinancingOwner personal(UUID userId) {
        return new FinancingOwner(userId, null);
    }

    public static FinancingOwner group(UUID groupId) {
        return new FinancingOwner(null, groupId);
    }

    public boolean isGroup() {
        return groupId != null;
    }
}
