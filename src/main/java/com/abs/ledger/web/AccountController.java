package com.abs.ledger.web;

import com.abs.ledger.domain.Account;
import com.abs.ledger.service.LedgerService;
import com.abs.ledger.web.dto.AccountResponse;
import com.abs.ledger.web.dto.BalanceResponse;
import com.abs.ledger.web.dto.CreateAccountRequest;
import com.abs.ledger.web.dto.StatementResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final LedgerService ledger;

    public AccountController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = ledger.createAccount(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable UUID id) {
        return new BalanceResponse(id, ledger.balance(id));
    }

    @GetMapping("/{id}/entries")
    public StatementResponse entries(@PathVariable UUID id) {
        return StatementResponse.of(id, ledger.entriesFor(id));
    }
}
