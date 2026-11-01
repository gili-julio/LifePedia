package com.lifepedia.api.financing;

import java.util.UUID;

/**
 * Dono dos dados e quem age.
 *
 * @param admin pode excluir qualquer item (pessoal: sempre; grupo: administrador)
 */
public record FinancingScope(FinancingOwner owner, UUID userId, boolean admin) {

    public static FinancingScope personal(UUID userId) {
        return new FinancingScope(FinancingOwner.personal(userId), userId, true);
    }

    public boolean canDelete(UUID createdBy) {
        return admin || userId.equals(createdBy);
    }
}
