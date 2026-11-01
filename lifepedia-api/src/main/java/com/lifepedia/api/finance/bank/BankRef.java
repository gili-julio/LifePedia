package com.lifepedia.api.finance.bank;

import com.lifepedia.api.shared.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;

import java.util.Locale;

/**
 * Banco de um item.
 *
 * @param code código de {@link Bank}
 * @param name nome digitado, só em {@link Bank#OTHER}
 * @param kind conta ou cartão
 */
@Embeddable
public record BankRef(
        @Column(name = "bank_code", length = 30) @Size(max = 30, message = "Banco inválido.") String code,
        @Column(name = "bank_name", length = 60) @Size(max = 60, message = "Nome do banco muito longo.") String name,
        @Enumerated(EnumType.STRING) @Column(name = "bank_kind", length = 10) BankKind kind) {

    /** Confere o banco vindo de fora: sem código = sem banco; nome só em OTHER; padrão conta. */
    public static BankRef clean(BankRef ref) {
        if (ref == null || ref.code() == null || ref.code().isBlank()) return null;
        Bank bank = Bank.of(ref.code())
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "Banco inválido."));
        BankKind kind = ref.kind() == null ? BankKind.ACCOUNT : ref.kind();
        if (bank != Bank.OTHER) return new BankRef(bank.name(), null, kind);
        if (ref.name() == null || ref.name().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Informe o nome do banco.");
        }
        return new BankRef(bank.name(), ref.name().trim(), kind);
    }

    /** Identifica o banco, sem olhar conta ou cartão; em OTHER, pelo nome digitado. */
    public String key() {
        return Bank.OTHER.name().equals(code) ? "OTHER:" + name.trim().toLowerCase(Locale.ROOT) : code;
    }

    public String label() {
        return Bank.OTHER.name().equals(code) ? name : Bank.of(code).map(Bank::label).orElse(code);
    }
}
