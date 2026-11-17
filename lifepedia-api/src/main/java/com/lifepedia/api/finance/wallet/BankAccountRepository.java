package com.lifepedia.api.finance.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    List<BankAccount> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<BankAccount> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
