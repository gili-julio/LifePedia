package com.lifepedia.api.finance;

import com.lifepedia.api.shared.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstallmentsTest {

    @Test
    void parsesInstallmentAtTheEnd() {
        assertThat(Installments.parse("Loja X (3/10)")).hasValueSatisfying(i -> {
            assertThat(i.base()).isEqualTo("Loja X");
            assertThat(i.number()).isEqualTo(3);
            assertThat(i.total()).isEqualTo(10);
        });
        assertThat(Installments.parse("Loja X(03 / 10)")).isPresent();
        assertThat(Installments.parse("Loja X")).isEmpty();
        assertThat(Installments.parse("(3/10) Loja")).isEmpty();
        assertThat(Installments.parse("Loja (11/10)")).isEmpty();
        assertThat(Installments.parse("Loja (1/1)")).isEmpty();
    }

    @Test
    void followingMeansSamePurchaseAndLaterNumber() {
        Installments.Installment third = Installments.parse("Loja (3/10)").orElseThrow();
        assertThat(third.isFollowedBy("loja (04/10)")).isTrue();
        assertThat(third.isFollowedBy("Loja (3/10)")).isFalse();
        assertThat(third.isFollowedBy("Loja (2/10)")).isFalse();
        assertThat(third.isFollowedBy("Loja (4/12)")).isFalse();
        assertThat(third.isFollowedBy("Loja Nova (4/10)")).isFalse();
    }

    @Test
    void shiftNeverGoesBeforePurchaseMonth() {
        LocalDate bought = LocalDate.of(2026, 1, 20);
        assertThat(Installments.shift(YearMonth.of(2026, 3), -2, bought, "Loja (2/4)")).isEqualTo(YearMonth.of(2026, 1));
        assertThatThrownBy(() -> Installments.shift(YearMonth.of(2026, 3), -3, bought, "Loja (2/4)"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("01/2026");
    }
}
