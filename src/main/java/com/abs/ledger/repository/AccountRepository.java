package com.abs.ledger.repository;

import com.abs.ledger.domain.Account;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByName(String name);

    boolean existsByName(String name);

    /**
     * Load an account under a row lock held until the surrounding transaction
     * commits, so two concurrent withdrawals cannot both read a sufficient
     * balance and overspend. Callers acquire these in stable id order to avoid
     * deadlocking opposing transfers.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
