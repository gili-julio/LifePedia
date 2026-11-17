package com.lifepedia.api.finance.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByCardIdAndMonth(UUID cardId, YearMonth month);

    List<Invoice> findByCardIdIn(Collection<UUID> cardIds);
}
