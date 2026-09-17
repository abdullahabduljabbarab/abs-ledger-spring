package com.abs.ledger.service;

import com.abs.ledger.domain.Account;
import com.abs.ledger.domain.LedgerEntry;
import com.abs.ledger.domain.Transaction;
import com.abs.ledger.domain.TransactionType;
import com.abs.ledger.error.ConflictException;
import com.abs.ledger.error.NotFoundException;
import com.abs.ledger.error.UnprocessableException;
import com.abs.ledger.repository.AccountRepository;
import com.abs.ledger.repository.LedgerEntryRepository;
import com.abs.ledger.repository.TransactionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ledger's only writer. Every posting is double-entry: the entries of one
 * transaction always sum to zero, deposits and withdrawals balancing against the
 * seeded External Clearing account and transfers between two customer accounts.
 * Balances are derived from the entries, never stored. Correctness is enforced in
 * two places at once: this service, and the database constraints the migration
 * installs (positive amounts, a unique idempotency key, foreign keys, and an
 * append-only trigger on the entries table).
 */
@Service
public class LedgerService {

    static final String CLEARING_ACCOUNT_NAME = "External Clearing";

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final LedgerEntryRepository entries;

    public LedgerService(AccountRepository accounts, TransactionRepository transactions,
                         LedgerEntryRepository entries) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.entries = entries;
    }

    @Transactional
    public Account createAccount(String name) {
        if (accounts.existsByName(name)) {
            throw new ConflictException("Account name already in use: " + name);
        }
        return accounts.save(new Account(name, false));
    }

    @Transactional(readOnly = true)
    public BigDecimal balance(UUID accountId) {
        requireAccountExists(accountId);
        return entries.sumByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> entriesFor(UUID accountId) {
        requireAccountExists(accountId);
        return entries.findAccountEntries(accountId);
    }

    /** The outcome of posting: the transaction, and whether it was newly created
     * (201) or an idempotent replay of an existing one (200). */
    public record PostResult(Transaction transaction, boolean created) {
    }

    @Transactional
    public PostResult post(CreateTransactionCommand cmd) {
        String requestHash = requestHash(cmd);

        Optional<Transaction> existing = transactions.findByIdempotencyKey(cmd.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }

        Posting posting = switch (cmd.type()) {
            case DEPOSIT -> buildDeposit(cmd, requestHash);
            case WITHDRAWAL -> buildWithdrawal(cmd, requestHash);
            case TRANSFER -> buildTransfer(cmd, requestHash);
        };

        // Defensive: a transaction that does not balance must never be persisted.
        // The database refuses it too, but failing here keeps the reason precise.
        BigDecimal sum = posting.entries().stream()
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Transaction entries do not sum to zero: " + sum);
        }

        try {
            transactions.saveAndFlush(posting.transaction());
        } catch (DataIntegrityViolationException race) {
            // A concurrent request inserted the same idempotency key first; the
            // unique constraint rejected this one. Return the winner's result.
            Transaction winner = transactions.findByIdempotencyKey(cmd.idempotencyKey())
                    .orElseThrow(() -> race);
            return replay(winner, requestHash);
        }
        entries.saveAll(posting.entries());
        return new PostResult(posting.transaction(), true);
    }

    private PostResult replay(Transaction existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ConflictException("Idempotency key already used with different parameters");
        }
        return new PostResult(existing, false);
    }

    private record Posting(Transaction transaction, List<LedgerEntry> entries) {
    }

    private Posting buildDeposit(CreateTransactionCommand cmd, String requestHash) {
        UUID accountId = require(cmd.accountId(), "account_id is required for a deposit");
        Account account = requireAccount(accountId);
        Account clearing = clearingAccount();

        Transaction txn = new Transaction(TransactionType.DEPOSIT, cmd.amount(),
                cmd.idempotencyKey(), requestHash, cmd.reference());
        return new Posting(txn, List.of(
                new LedgerEntry(txn, clearing, cmd.amount().negate()),
                new LedgerEntry(txn, account, cmd.amount())
        ));
    }

    private Posting buildWithdrawal(CreateTransactionCommand cmd, String requestHash) {
        UUID accountId = require(cmd.accountId(), "account_id is required for a withdrawal");
        Account account = lockedAccount(accountId);
        Account clearing = clearingAccount();

        if (entries.sumByAccountId(accountId).compareTo(cmd.amount()) < 0) {
            throw new UnprocessableException("Insufficient balance");
        }

        Transaction txn = new Transaction(TransactionType.WITHDRAWAL, cmd.amount(),
                cmd.idempotencyKey(), requestHash, cmd.reference());
        return new Posting(txn, List.of(
                new LedgerEntry(txn, account, cmd.amount().negate()),
                new LedgerEntry(txn, clearing, cmd.amount())
        ));
    }

    private Posting buildTransfer(CreateTransactionCommand cmd, String requestHash) {
        UUID fromId = require(cmd.fromAccountId(), "from_account_id is required for a transfer");
        UUID toId = require(cmd.toAccountId(), "to_account_id is required for a transfer");
        if (fromId.equals(toId)) {
            throw new UnprocessableException("Cannot transfer to the same account");
        }

        // Lock both accounts in stable id order so two opposing transfers between
        // the same pair cannot deadlock.
        Map<UUID, Account> locked = new LinkedHashMap<>();
        Stream.of(fromId, toId).sorted().forEach(id -> locked.put(id, lockedAccount(id)));
        Account from = locked.get(fromId);
        Account to = locked.get(toId);

        if (entries.sumByAccountId(fromId).compareTo(cmd.amount()) < 0) {
            throw new UnprocessableException("Insufficient balance");
        }

        Transaction txn = new Transaction(TransactionType.TRANSFER, cmd.amount(),
                cmd.idempotencyKey(), requestHash, cmd.reference());
        return new Posting(txn, List.of(
                new LedgerEntry(txn, from, cmd.amount().negate()),
                new LedgerEntry(txn, to, cmd.amount())
        ));
    }

    private Account lockedAccount(UUID id) {
        return accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Account not found: " + id));
    }

    private Account requireAccount(UUID id) {
        return accounts.findById(id)
                .orElseThrow(() -> new NotFoundException("Account not found: " + id));
    }

    private void requireAccountExists(UUID id) {
        if (!accounts.existsById(id)) {
            throw new NotFoundException("Account not found: " + id);
        }
    }

    private Account clearingAccount() {
        return accounts.findByName(CLEARING_ACCOUNT_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "External Clearing account is not seeded"));
    }

    private static UUID require(UUID value, String message) {
        if (value == null) {
            throw new UnprocessableException(message);
        }
        return value;
    }

    private static String requestHash(CreateTransactionCommand cmd) {
        String canonical = String.join("|",
                cmd.type().name(),
                cmd.amount().toPlainString(),
                String.valueOf(cmd.accountId()),
                String.valueOf(cmd.fromAccountId()),
                String.valueOf(cmd.toAccountId()),
                cmd.reference() == null ? "" : cmd.reference());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
