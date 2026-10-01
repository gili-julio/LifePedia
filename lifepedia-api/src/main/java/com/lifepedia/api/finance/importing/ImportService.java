package com.lifepedia.api.finance.importing;

import com.lifepedia.api.finance.importing.ImportDtos.FixedAdjustment;
import com.lifepedia.api.finance.importing.ImportDtos.ImportRequest;
import com.lifepedia.api.finance.importing.ImportDtos.ImportResponse;
import com.lifepedia.api.finance.recurring.RecurringEntryService;
import com.lifepedia.api.finance.transaction.TransactionDtos.TransactionRequest;
import com.lifepedia.api.finance.transaction.TransactionService;
import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ImportService {

    private final TransactionService transactionService;
    private final RecurringEntryService recurringEntryService;

    public ImportService(TransactionService transactionService, RecurringEntryService recurringEntryService) {
        this.transactionService = transactionService;
        this.recurringEntryService = recurringEntryService;
    }

    /** Tudo ou nada. */
    @Transactional
    public ImportResponse importAll(UUID userId, ImportRequest request) {
        List<TransactionRequest> transactions = request.transactionsOrEmpty();
        List<FixedAdjustment> adjustments = request.fixedAdjustmentsOrEmpty();
        if (transactions.isEmpty() && adjustments.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Nada para importar.");
        }
        if (!transactions.isEmpty()) {
            transactionService.createBatch(userId, transactions);
        }
        for (int i = 0; i < adjustments.size(); i++) {
            FixedAdjustment adjustment = adjustments.get(i);
            try {
                recurringEntryService.adjust(userId, adjustment.recurringEntryId(), adjustment.month(), adjustment.amount());
            } catch (BusinessException | NotFoundException e) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Ajuste de fixo " + (i + 1) + ": " + e.getMessage());
            }
        }
        return new ImportResponse(transactions.size(), adjustments.size());
    }
}
