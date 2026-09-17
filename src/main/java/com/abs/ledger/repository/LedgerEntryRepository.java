package com.abs.ledger.repository;

import com.abs.ledger.domain.LedgerEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * A balance is derived from the entries, never stored as a mutable number.
     * Coalesced to zero so an account with no entries reads as 0.00.
     */
    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.account.id = :accountId")
    BigDecimal sumByAccountId(@Param("accountId") UUID accountId);

    /**
     * An account's entries in posting order, with the owning transaction fetched
     * in the same query so the statement can read it without an open session.
     */
    @Query("select e from LedgerEntry e join fetch e.transaction "
            + "where e.account.id = :accountId order by e.createdAt asc, e.id asc")
    List<LedgerEntry> findAccountEntries(@Param("accountId") UUID accountId);

    /** The global invariant: every entry in the ledger sums to exactly zero. */
    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e")
    BigDecimal sumAll();
}
