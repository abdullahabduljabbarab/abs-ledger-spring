package com.abs.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abs.ledger.domain.TransactionType;
import com.abs.ledger.repository.LedgerEntryRepository;
import com.abs.ledger.service.CreateTransactionCommand;
import com.abs.ledger.service.LedgerService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class LedgerConcurrencyAndInvariantTest extends AbstractIntegrationTest {

    @Autowired
    LedgerService ledger;

    @Autowired
    LedgerEntryRepository entries;

    @Autowired
    JdbcTemplate jdbc;

    private UUID account(String prefix) {
        return ledger.createAccount(prefix + "-" + UUID.randomUUID()).getId();
    }

    private void deposit(UUID account, String amount) {
        ledger.post(new CreateTransactionCommand(UUID.randomUUID().toString(),
                TransactionType.DEPOSIT, new BigDecimal(amount), account, null, null, null));
    }

    private void transfer(UUID from, UUID to, BigDecimal amount) {
        ledger.post(new CreateTransactionCommand(UUID.randomUUID().toString(),
                TransactionType.TRANSFER, amount, null, from, to, null));
    }

    /**
     * The whole pitch in one test. Many transfers run in parallel against the same
     * pair of accounts. Pessimistic row locks, taken in stable id order, serialise
     * the balance-check-and-post so nothing is lost and the books still balance.
     */
    @Test
    void concurrent_transfers_keep_the_books_balanced() throws Exception {
        UUID a = account("conc-a");
        UUID b = account("conc-b");
        deposit(a, "1000.00");
        deposit(b, "1000.00");

        int perDirection = 50;
        BigDecimal amount = new BigDecimal("10.00");

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < perDirection; i++) {
            tasks.add(() -> {
                transfer(a, b, amount);
                return null;
            });
            tasks.add(() -> {
                transfer(b, a, amount);
                return null;
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<Void>> futures = pool.invokeAll(tasks);
            for (Future<Void> f : futures) {
                f.get();  // surfaces any worker exception, including a lost race
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }

        // Equal transfers in each direction net to zero, so each account is back
        // where it started, and the whole ledger still sums to zero.
        assertThat(ledger.balance(a)).isEqualByComparingTo("1000.00");
        assertThat(ledger.balance(b)).isEqualByComparingTo("1000.00");
        assertThat(entries.sumAll()).isEqualByComparingTo("0.00");
    }

    @Test
    void every_transaction_balances_to_zero() {
        UUID a = account("bal-a");
        UUID b = account("bal-b");
        deposit(a, "100.00");
        transfer(a, b, new BigDecimal("40.00"));

        Integer unbalanced = jdbc.queryForObject(
                "select count(*) from (select transaction_id from ledger_entries "
                        + "group by transaction_id having sum(amount) <> 0) t",
                Integer.class);
        assertThat(unbalanced).isZero();
    }

    /**
     * Immutability is enforced by the database, not just by the absence of setters.
     * The append-only trigger rejects any UPDATE or DELETE on a posted entry.
     */
    @Test
    void posted_entries_cannot_be_updated_or_deleted() {
        UUID acct = account("immut");
        deposit(acct, "25.00");
        UUID entryId = jdbc.queryForObject(
                "select id from ledger_entries where account_id = ? limit 1",
                UUID.class, acct);

        assertThatThrownBy(() ->
                jdbc.update("update ledger_entries set amount = amount where id = ?", entryId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() ->
                jdbc.update("delete from ledger_entries where id = ?", entryId))
                .isInstanceOf(DataAccessException.class);

        assertThat(ledger.balance(acct)).isEqualByComparingTo("25.00");
    }

    /**
     * The zero-sum invariant is enforced by the database, not only by the service.
     * A deferred constraint trigger rejects, at commit, a transaction whose entries
     * do not sum to zero, even when inserted by direct SQL that bypasses the service.
     */
    @Test
    void an_unbalanced_transaction_cannot_be_posted_by_direct_sql() {
        UUID acct = account("unbal");
        UUID txnId = UUID.randomUUID();
        jdbc.update("insert into transactions (id, idempotency_key, type, amount, request_hash) "
                        + "values (?, ?, 'DEPOSIT', 50.00, ?)",
                txnId, "unbal-" + txnId, "x".repeat(64));

        // A single entry leaves the transaction summing to 50, not zero, so the
        // deferred constraint trigger rejects it at commit.
        assertThatThrownBy(() ->
                jdbc.update("insert into ledger_entries (id, transaction_id, account_id, amount) "
                                + "values (?, ?, ?, 50.00)",
                        UUID.randomUUID(), txnId, acct))
                .isInstanceOf(DataAccessException.class);
    }
}
