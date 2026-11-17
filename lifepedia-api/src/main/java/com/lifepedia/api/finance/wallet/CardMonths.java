package com.lifepedia.api.finance.wallet;

import com.lifepedia.api.finance.Installments;
import com.lifepedia.api.finance.Installments.Installment;
import com.lifepedia.api.finance.bank.BankKind;
import com.lifepedia.api.finance.bank.BankRef;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/** Fatura em que cai um lançamento no cartão, para quem lança sem informar o mês. */
@Component
public class CardMonths {

    private final CardRepository cards;

    public CardMonths(CardRepository cards) {
        this.cards = cards;
    }

    /** Vazio se não é cartão ou o banco não tem cartão cadastrado. Parcela "(3/10)" vai duas faturas depois da compra. */
    public Optional<YearMonth> monthOf(UUID userId, BankRef bank, LocalDate date, String description) {
        if (bank == null || bank.kind() != BankKind.CARD || date == null) return Optional.empty();
        int installment = Installments.parse(description).map(Installment::number).orElse(1);
        return cards.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(c -> c.matches(bank))
                .findFirst()
                .map(c -> c.cycle().invoiceOf(date, installment));
    }
}
