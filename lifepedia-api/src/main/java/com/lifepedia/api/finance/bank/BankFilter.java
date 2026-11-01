package com.lifepedia.api.finance.bank;

import com.lifepedia.api.shared.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

/**
 * Filtro por banco das listas e do resumo.
 *
 * @param code código de {@link Bank}
 * @param name nome em minúsculas, em OTHER
 * @param none só itens sem banco
 */
public record BankFilter(String code, String name, BankKind kind, boolean none) {

    public static final String NONE = "NONE";
    public static final BankFilter ALL = new BankFilter(null, null, null, false);

    /** @param bank chave da linha do relatório ({@link BankRef#key()}), {@code NONE} ou nulo */
    public static BankFilter of(String bank, BankKind kind) {
        if (bank == null || bank.isBlank()) return new BankFilter(null, null, kind, false);
        if (NONE.equals(bank)) return new BankFilter(null, null, null, true);
        if (bank.startsWith("OTHER:")) {
            return new BankFilter(Bank.OTHER.name(), bank.substring(6).trim().toLowerCase(Locale.ROOT), kind, false);
        }
        Bank known = Bank.of(bank).orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "Banco inválido."));
        return new BankFilter(known.name(), null, kind, false);
    }

    public boolean matches(BankRef bank) {
        if (none) return bank == null;
        if (code == null && kind == null) return true;
        if (bank == null) return false;
        return (code == null || code.equals(bank.code()))
                && (name == null || name.equals(bank.name().trim().toLowerCase(Locale.ROOT)))
                && (kind == null || kind == bank.kind());
    }
}
