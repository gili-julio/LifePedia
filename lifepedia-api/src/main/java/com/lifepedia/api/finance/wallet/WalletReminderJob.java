package com.lifepedia.api.finance.wallet;

import com.lifepedia.api.finance.wallet.WalletDtos.InvoiceSummary;
import com.lifepedia.api.finance.wallet.WalletService.Owner;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Todo dia às 9h, nos cartões com aviso ligado: fatura fechando em até 3 dias, fatura fechada vencendo em até 3 dias
 * e meta da fatura aberta em 80% e 100%. Cada aviso uma vez por fatura.
 */
@Component
public class WalletReminderJob {

    static final int DAYS_BEFORE = 3;
    private static final int[] BUDGET_STEPS = {100, 80};

    /** O que o aviso conta. */
    public enum Kind {
        CLOSING,
        DUE,
        BUDGET
    }

    private final CardRepository cards;
    private final InvoiceRepository invoices;
    private final WalletService wallet;
    private final NotificationService notifications;
    private final Clock clock;

    public WalletReminderJob(CardRepository cards, InvoiceRepository invoices, WalletService wallet,
                             NotificationService notifications, Clock clock) {
        this.cards = cards;
        this.invoices = invoices;
        this.wallet = wallet;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "${lifepedia.time.zone}")
    public void run() {
        remind();
    }

    /** Devolve quantos avisos saíram. */
    @Transactional
    public int remind() {
        LocalDate today = LocalDate.now(clock);
        Map<UUID, List<Card>> byUser = cards.findByRemindersTrue().stream().collect(Collectors.groupingBy(Card::getUserId));
        int sent = 0;
        for (Map.Entry<UUID, List<Card>> entry : byUser.entrySet()) {
            Owner owner = wallet.owner(entry.getKey());
            Map<UUID, CardBook> books = wallet.books(owner, today, null);
            for (Card card : entry.getValue()) {
                sent += remind(card, books.get(card.getId()));
            }
        }
        return sent;
    }

    private int remind(Card card, CardBook book) {
        int sent = 0;
        InvoiceSummary open = book.summary(book.current());
        if (open.total().signum() > 0 && open.daysToClose() > 0 && open.daysToClose() <= DAYS_BEFORE) {
            Invoice state = state(card, open.month());
            if (!state.isWarnedClosing()) {
                state.setWarnedClosing(true);
                sent += send(card, Kind.CLOSING, open.total(), open.closesOn(), null);
            }
        }
        InvoiceSummary pending = book.pending().orElse(null);
        if (pending != null && pending.daysToDue() >= 0 && pending.daysToDue() <= DAYS_BEFORE) {
            Invoice state = state(card, pending.month());
            if (!state.isWarnedDue()) {
                state.setWarnedDue(true);
                sent += send(card, Kind.DUE, pending.total(), pending.dueOn(), null);
            }
        }
        if (card.getBudget() != null && open.total().signum() > 0) {
            int percent = open.total().multiply(BigDecimal.valueOf(100)).divide(card.getBudget(), 0, RoundingMode.DOWN).intValue();
            for (int step : BUDGET_STEPS) {
                if (percent < step) continue;
                Invoice state = state(card, open.month());
                if (state.getWarnedBudget() < step) {
                    state.setWarnedBudget(step);
                    sent += send(card, Kind.BUDGET, open.total(), open.closesOn(), percent);
                }
                break;
            }
        }
        return sent;
    }

    private Invoice state(Card card, YearMonth month) {
        return invoices.findByCardIdAndMonth(card.getId(), month).orElseGet(() -> invoices.save(new Invoice(card.getId(), month)));
    }

    private int send(Card card, Kind kind, BigDecimal amount, LocalDate date, Integer percent) {
        Map<String, String> data = new HashMap<>();
        data.put("cardId", card.getId().toString());
        data.put("cardName", card.label());
        data.put("kind", kind.name());
        data.put("amount", amount.toPlainString());
        data.put("date", date.toString());
        if (percent != null) data.put("percent", percent.toString());
        notifications.notify(card.getUserId(), NotificationType.CARD_REMINDER, null, null, data);
        return 1;
    }
}
