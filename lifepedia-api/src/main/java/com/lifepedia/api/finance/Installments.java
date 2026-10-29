package com.lifepedia.api.finance;

import com.lifepedia.api.shared.BusinessException;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parcela pelo nome do lançamento ("Loja (3/10)"): adiantar ou adiar uma parcela e, se pedido, as seguintes. */
public final class Installments {

    private static final Pattern PATTERN = Pattern.compile("^(.*\\S)\\s*\\((\\d{1,3})\\s*/\\s*(\\d{1,3})\\)$");

    private Installments() {
    }

    /** @param following também as próximas parcelas da mesma compra, com o mesmo deslocamento */
    public record MoveRequest(@NotNull(message = "Informe o mês.") YearMonth month, Boolean following) {
    }

    /** @param moved quantos lançamentos mudaram de mês */
    public record MoveResult(int moved) {
    }

    /** @param base nome sem a parcela */
    public record Installment(String base, int number, int total) {

        /** Parcela posterior desta mesma compra (mesmo nome e mesmo total). */
        public boolean isFollowedBy(String description) {
            return parse(description).filter(o -> o.base.equalsIgnoreCase(base) && o.total == total && o.number > number)
                    .isPresent();
        }
    }

    public static Optional<Installment> parse(String description) {
        if (description == null) return Optional.empty();
        Matcher m = PATTERN.matcher(description.trim());
        if (!m.matches()) return Optional.empty();
        int number = Integer.parseInt(m.group(2));
        int total = Integer.parseInt(m.group(3));
        if (number < 1 || total < 2 || number > total) return Optional.empty();
        return Optional.of(new Installment(m.group(1), number, total));
    }

    /** Parcela de onde partem as seguintes; 400 se o nome não tiver "(n/total)". */
    public static Installment require(String description) {
        return parse(description).orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST,
                "Este lançamento não é uma parcela (o nome precisa terminar com algo como \"(3/10)\")."));
    }

    /** Mês deslocado; 400 se ficar antes do mês da data (compra). */
    public static YearMonth shift(YearMonth month, long months, LocalDate date, String description) {
        YearMonth moved = month.plusMonths(months);
        YearMonth bought = YearMonth.from(date);
        if (moved.isBefore(bought)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "\"%s\" ficaria antes do mês da compra (%02d/%d)."
                    .formatted(description, bought.getMonthValue(), bought.getYear()));
        }
        return moved;
    }
}
