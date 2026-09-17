package com.abs.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class LedgerApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private UUID createAccount(String name) throws Exception {
        String body = mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private void deposit(UUID account, String amount, String key) throws Exception {
        mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotency_key\":\"" + key + "\",\"type\":\"deposit\","
                                + "\"amount\":" + amount + ",\"account_id\":\"" + account + "\"}"))
                .andExpect(status().isCreated());
    }

    private BigDecimal balance(UUID account) throws Exception {
        String body = mvc.perform(get("/accounts/" + account + "/balance"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Compare money by value, not string: JSON round-trips 100.00 as 100.0.
        return new BigDecimal(json.readTree(body).get("balance").asText());
    }

    @Test
    void deposit_credits_the_account() throws Exception {
        UUID account = createAccount("acct-" + UUID.randomUUID());
        deposit(account, "100.00", UUID.randomUUID().toString());
        assertThat(balance(account)).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void withdrawal_debits_and_cannot_overspend() throws Exception {
        UUID account = createAccount("acct-" + UUID.randomUUID());
        deposit(account, "100.00", UUID.randomUUID().toString());

        mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotency_key\":\"" + UUID.randomUUID() + "\",\"type\":\"withdrawal\","
                                + "\"amount\":30.00,\"account_id\":\"" + account + "\"}"))
                .andExpect(status().isCreated());
        assertThat(balance(account)).isEqualByComparingTo(new BigDecimal("70.00"));

        // 80 > 70 remaining, so this must be refused and the balance unchanged.
        mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotency_key\":\"" + UUID.randomUUID() + "\",\"type\":\"withdrawal\","
                                + "\"amount\":80.00,\"account_id\":\"" + account + "\"}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(balance(account)).isEqualByComparingTo(new BigDecimal("70.00"));
    }

    @Test
    void transfer_moves_money_and_conserves_the_pair() throws Exception {
        UUID from = createAccount("from-" + UUID.randomUUID());
        UUID to = createAccount("to-" + UUID.randomUUID());
        deposit(from, "100.00", UUID.randomUUID().toString());

        mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotency_key\":\"" + UUID.randomUUID() + "\",\"type\":\"transfer\","
                                + "\"amount\":40.00,\"from_account_id\":\"" + from + "\","
                                + "\"to_account_id\":\"" + to + "\"}"))
                .andExpect(status().isCreated());

        assertThat(balance(from)).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(balance(to)).isEqualByComparingTo(new BigDecimal("40.00"));
    }

    @Test
    void duplicate_idempotency_key_replays_with_200_and_applies_once() throws Exception {
        UUID account = createAccount("acct-" + UUID.randomUUID());
        String key = UUID.randomUUID().toString();
        String req = "{\"idempotency_key\":\"" + key + "\",\"type\":\"deposit\","
                + "\"amount\":50.00,\"account_id\":\"" + account + "\"}";

        String first = mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Same key, same body: a replay. 200, same transaction id, applied once.
        String second = mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode a = json.readTree(first);
        JsonNode b = json.readTree(second);
        assertThat(b.get("id").asText()).isEqualTo(a.get("id").asText());
        assertThat(balance(account)).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void same_key_different_parameters_is_a_conflict() throws Exception {
        UUID account = createAccount("acct-" + UUID.randomUUID());
        String key = UUID.randomUUID().toString();

        deposit(account, "50.00", key);

        mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotency_key\":\"" + key + "\",\"type\":\"deposit\","
                                + "\"amount\":999.00,\"account_id\":\"" + account + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void balance_of_unknown_account_is_404() throws Exception {
        mvc.perform(get("/accounts/" + UUID.randomUUID() + "/balance"))
                .andExpect(status().isNotFound());
    }

    @Test
    void negative_amount_is_rejected_by_validation() throws Exception {
        UUID account = createAccount("acct-" + UUID.randomUUID());
        mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotency_key\":\"" + UUID.randomUUID() + "\",\"type\":\"deposit\","
                                + "\"amount\":-5.00,\"account_id\":\"" + account + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void statement_lists_entries_with_running_balance() throws Exception {
        UUID account = createAccount("acct-" + UUID.randomUUID());
        deposit(account, "100.00", UUID.randomUUID().toString());
        mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotency_key\":\"" + UUID.randomUUID() + "\",\"type\":\"withdrawal\","
                                + "\"amount\":30.00,\"account_id\":\"" + account + "\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/accounts/" + account + "/entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closing_balance").value(70.00))
                .andExpect(jsonPath("$.entries.length()").value(2));
    }

    @Test
    void duplicate_account_name_is_conflict() throws Exception {
        String name = "dup-" + UUID.randomUUID();
        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void self_transfer_is_rejected() throws Exception {
        UUID account = createAccount("self-" + UUID.randomUUID());
        deposit(account, "100.00", UUID.randomUUID().toString());
        mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotency_key\":\"" + UUID.randomUUID() + "\",\"type\":\"transfer\","
                                + "\"amount\":10.00,\"from_account_id\":\"" + account + "\","
                                + "\"to_account_id\":\"" + account + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transfer_with_insufficient_funds_is_rejected() throws Exception {
        UUID from = createAccount("tf-from-" + UUID.randomUUID());
        UUID to = createAccount("tf-to-" + UUID.randomUUID());
        deposit(from, "10.00", UUID.randomUUID().toString());
        mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotency_key\":\"" + UUID.randomUUID() + "\",\"type\":\"transfer\","
                                + "\"amount\":50.00,\"from_account_id\":\"" + from + "\","
                                + "\"to_account_id\":\"" + to + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
