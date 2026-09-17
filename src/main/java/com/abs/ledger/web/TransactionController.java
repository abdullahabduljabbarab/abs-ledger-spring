package com.abs.ledger.web;

import com.abs.ledger.service.CreateTransactionCommand;
import com.abs.ledger.service.LedgerService;
import com.abs.ledger.service.LedgerService.PostResult;
import com.abs.ledger.web.dto.CreateTransactionRequest;
import com.abs.ledger.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final LedgerService ledger;

    public TransactionController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> post(@Valid @RequestBody CreateTransactionRequest request) {
        // Normalise to two decimal places so "10" and "10.00" are the same money
        // and hash to the same idempotent request. Widening scale never rounds.
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);

        CreateTransactionCommand command = new CreateTransactionCommand(
                request.idempotencyKey(), request.type(), amount,
                request.accountId(), request.fromAccountId(), request.toAccountId(),
                request.reference());

        PostResult result = ledger.post(command);
        // A newly posted transaction is 201; an idempotent replay of an existing
        // one is 200, so a duplicate request is visibly not a second posting.
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(TransactionResponse.from(result.transaction()));
    }
}
