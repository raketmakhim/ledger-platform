package com.raketmakhim.ledger.accounting.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.raketmakhim.ledger.TestcontainersConfiguration;
import com.raketmakhim.ledger.accounting.application.IdGenerator;
import com.raketmakhim.ledger.accounting.adapter.persistence.JpaAccountRepository;
import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountStatus;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;
import com.raketmakhim.ledger.accounting.domain.money.Money;

/** POST /transfers through the whole app (controller, service, JPA) against a real Postgres. */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransferEndpointTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    // every account in these tests belongs to this customer, who is also the caller
    private static final UUID OWNER = new UUID(7, 7);

    @Autowired
    MockMvc mvc;

    @Autowired
    JpaAccountRepository accounts;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IdGenerator ids;

    private UUID newCustomer(String balance) {
        UUID id = ids.newId();
        accounts.save(new Account(id, OWNER, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE, gbp(balance), 0, Instant.now()));
        return id;
    }

    private Money balance(UUID accountId) {
        return accounts.findById(accountId).orElseThrow().balance();
    }

    private static Money gbp(String amount) {
        return new Money(new BigDecimal(amount), GBP);
    }

    private int entryCount(String reference) {
        return jdbc.queryForObject("SELECT count(*) FROM journal_entry WHERE reference = ?", Integer.class, reference);
    }

    private ResultActions transfer(UUID from, UUID to, String amount, String reference) throws Exception {
        return transferAs(OWNER, from, to, amount, reference);
    }

    private ResultActions transferAs(UUID caller, UUID from, UUID to, String amount, String reference) throws Exception {
        return transferWithKey(caller, ids.newId().toString(), from, to, amount, reference);
    }

    private ResultActions transferWithKey(UUID caller, String key, UUID from, UUID to, String amount, String reference)
            throws Exception {
        String json = "{\"from\":\"" + from + "\",\"to\":\"" + to + "\",\"amount\":\"" + amount
                + "\",\"currency\":\"GBP\",\"reference\":\"" + reference + "\"}";
        return mvc.perform(post("/transfers").header("X-Customer-Id", caller.toString()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void aTransferOutOfSomeoneElsesAccountReturns404AndMovesNothing() throws Exception {
        UUID alice = newCustomer("100.00");
        UUID bob = newCustomer("0.00");
        UUID stranger = new UUID(7, 8);

        transferAs(stranger, alice, bob, "30.00", "E2E-STRANGER")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:ledger:problem:account-not-found"));

        assertThat(balance(alice)).isEqualTo(gbp("100.00"));
        assertThat(balance(bob)).isEqualTo(gbp("0.00"));
        assertThat(entryCount("E2E-STRANGER")).isZero();
    }

    @Test
    void aTransferWithoutACallerReturns401() throws Exception {
        UUID alice = newCustomer("100.00");
        UUID bob = newCustomer("0.00");

        mvc.perform(post("/transfers").header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"" + alice + "\",\"to\":\"" + bob
                                + "\",\"amount\":\"1.00\",\"currency\":\"GBP\",\"reference\":\"E2E-NOBODY\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:ledger:problem:unauthenticated"));

        assertThat(balance(alice)).isEqualTo(gbp("100.00"));
    }

    @Test
    void aTransferMovesTheMoneyAndRecordsTheEntry() throws Exception {
        UUID alice = newCustomer("100.00");
        UUID bob = newCustomer("5.00");

        transfer(alice, bob, "30.00", "E2E-OK")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("E2E-OK"));

        assertThat(balance(alice)).isEqualTo(gbp("70.00"));
        assertThat(balance(bob)).isEqualTo(gbp("35.00"));
        assertThat(entryCount("E2E-OK")).isEqualTo(1);
    }

    @Test
    void aTransferWithTooLittleMoneyIsRefusedAndChangesNothing() throws Exception {
        UUID alice = newCustomer("10.00");
        UUID bob = newCustomer("5.00");

        transfer(alice, bob, "30.00", "E2E-FAIL")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:ledger:problem:insufficient-funds"));

        assertThat(balance(alice)).isEqualTo(gbp("10.00"));
        assertThat(balance(bob)).isEqualTo(gbp("5.00"));
        assertThat(entryCount("E2E-FAIL")).isZero();
    }

    @Test
    void reusingAReferenceIsRefusedAndMovesNoMoneyTheSecondTime() throws Exception {
        UUID alice = newCustomer("100.00");
        UUID bob = newCustomer("0.00");

        transfer(alice, bob, "10.00", "E2E-DUP").andExpect(status().isCreated());
        transfer(alice, bob, "10.00", "E2E-DUP")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ledger:problem:duplicate-reference"));

        assertThat(balance(alice)).isEqualTo(gbp("90.00"));
        assertThat(balance(bob)).isEqualTo(gbp("10.00"));
        assertThat(entryCount("E2E-DUP")).isEqualTo(1);
    }

    @Test
    void aTransferFromAnAccountThatDoesNotExistReturns404() throws Exception {
        UUID bob = newCustomer("5.00");

        transfer(ids.newId(), bob, "1.00", "E2E-404")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:ledger:problem:account-not-found"));

        assertThat(balance(bob)).isEqualTo(gbp("5.00"));
        assertThat(entryCount("E2E-404")).isZero();
    }

    // --- idempotency: the same key is the same payment ---

    @Test
    void retryingWithTheSameKeyReplaysTheFirstResponseAndMovesMoneyOnce() throws Exception {
        UUID alice = newCustomer("100.00");
        UUID bob = newCustomer("0.00");

        String first = transferWithKey(OWNER, "E2E-KEY-1", alice, bob, "10.00", "E2E-KEY-REF")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String retry = transferWithKey(OWNER, "E2E-KEY-1", alice, bob, "10.00", "E2E-KEY-REF")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(retry).isEqualTo(first);
        assertThat(balance(alice)).isEqualTo(gbp("90.00"));
        assertThat(balance(bob)).isEqualTo(gbp("10.00"));
        assertThat(entryCount("E2E-KEY-REF")).isEqualTo(1);
    }

    @Test
    void theSameKeyForADifferentRequestReturns422AndMovesNothing() throws Exception {
        UUID alice = newCustomer("100.00");
        UUID bob = newCustomer("0.00");
        transferWithKey(OWNER, "E2E-KEY-2", alice, bob, "10.00", "E2E-KEY-2A").andExpect(status().isCreated());

        transferWithKey(OWNER, "E2E-KEY-2", alice, bob, "20.00", "E2E-KEY-2A")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:ledger:problem:idempotency-key-reuse"));

        assertThat(balance(alice)).isEqualTo(gbp("90.00"));
    }

    @Test
    void aRequestWithoutAnIdempotencyKeyReturns400() throws Exception {
        UUID alice = newCustomer("100.00");
        UUID bob = newCustomer("0.00");

        mvc.perform(post("/transfers").header("X-Customer-Id", OWNER.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"" + alice + "\",\"to\":\"" + bob
                                + "\",\"amount\":\"1.00\",\"currency\":\"GBP\",\"reference\":\"E2E-NOKEY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:ledger:problem:invalid-request"));

        assertThat(balance(alice)).isEqualTo(gbp("100.00"));
    }
}
