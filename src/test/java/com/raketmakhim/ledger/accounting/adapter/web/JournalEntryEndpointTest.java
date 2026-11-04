package com.raketmakhim.ledger.accounting.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.MvcResult;

import com.raketmakhim.ledger.TestcontainersConfiguration;
import com.raketmakhim.ledger.accounting.adapter.persistence.JpaAccountRepository;
import com.raketmakhim.ledger.accounting.application.DepositService;
import com.raketmakhim.ledger.accounting.application.IdGenerator;
import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountStatus;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.money.Money;

/** GET /journal-entries/{id} through the whole app against a real Postgres. */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JournalEntryEndpointTest {

    private static final Currency GBP = Currency.getInstance("GBP");

    @Autowired
    MockMvc mvc;

    @Autowired
    JpaAccountRepository accounts;

    @Autowired
    DepositService deposits;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IdGenerator ids;

    private UUID newCustomer(UUID owner, String balance) {
        UUID id = ids.newId();
        accounts.save(new Account(id, owner, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE,
                new Money(new BigDecimal(balance), GBP), 0, Instant.now()));
        return id;
    }

    private MvcResult transfer(UUID caller, UUID from, UUID to, String reference) throws Exception {
        return mvc.perform(post("/transfers").header("X-Customer-Id", caller.toString())
                        .header("Idempotency-Key", ids.newId().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"" + from + "\",\"to\":\"" + to
                                + "\",\"amount\":\"10.00\",\"currency\":\"GBP\",\"reference\":\"" + reference + "\"}"))
                .andExpect(status().isCreated()).andReturn();
    }

    private static String location(MvcResult created) {
        return created.getResponse().getHeader("Location");
    }

    @Test
    void theLocationOfATransferCanBeFetchedAndGivesTheSameBody() throws Exception {
        UUID alice = ids.newId();
        UUID from = newCustomer(alice, "100.00");
        UUID to = newCustomer(ids.newId(), "0.00");
        MvcResult created = transfer(alice, from, to, "READ-E2E-1");

        String fetched = mvc.perform(get(location(created)).header("X-Customer-Id", alice.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(fetched).isEqualTo(created.getResponse().getContentAsString());
    }

    @Test
    void theOwnerOfTheDestinationSeesBothPostings() throws Exception {
        UUID alice = ids.newId();
        UUID bob = ids.newId();
        UUID from = newCustomer(alice, "100.00");
        UUID to = newCustomer(bob, "0.00");
        MvcResult created = transfer(alice, from, to, "READ-E2E-2");

        mvc.perform(get(location(created)).header("X-Customer-Id", bob.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postings.length()").value(2))
                .andExpect(jsonPath("$.postings[0].accountId").value(from.toString()))
                .andExpect(jsonPath("$.postings[1].accountId").value(to.toString()));
    }

    @Test
    void aStrangerGetsTheSame404AsForAnUnknownId() throws Exception {
        UUID alice = ids.newId();
        MvcResult created = transfer(alice, newCustomer(alice, "100.00"), newCustomer(ids.newId(), "0.00"), "READ-E2E-3");
        UUID stranger = ids.newId();

        mvc.perform(get(location(created)).header("X-Customer-Id", stranger.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:ledger:problem:entry-not-found"));
        mvc.perform(get("/journal-entries/" + ids.newId()).header("X-Customer-Id", stranger.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:ledger:problem:entry-not-found"));
    }

    @Test
    void aDepositShowsTheCustomerOnlyTheirOwnPostingAndNeverTheBanksAccount() throws Exception {
        UUID owner = ids.newId();
        UUID account = newCustomer(owner, "0.00");
        JournalEntry deposit = deposits.deposit(account, new Money(new BigDecimal("25.00"), GBP), "READ-E2E-4");
        String clearing = accounts.findClearingAccount(GBP).orElseThrow().id().toString();

        String body = mvc.perform(get("/journal-entries/" + deposit.id()).header("X-Customer-Id", owner.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.postings.length()").value(1))
                .andExpect(jsonPath("$.postings[0].accountId").value(account.toString()))
                .andExpect(jsonPath("$.postings[0].amount").value("25.00"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(clearing);
    }

    @Test
    void readingAnEntryWithoutACallerReturns401() throws Exception {
        mvc.perform(get("/journal-entries/" + ids.newId()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"));
    }

    @Test
    void readingChangesNothing() throws Exception {
        UUID alice = ids.newId();
        MvcResult created = transfer(alice, newCustomer(alice, "100.00"), newCustomer(ids.newId(), "0.00"), "READ-E2E-5");
        int entriesBefore = jdbc.queryForObject("SELECT count(*) FROM journal_entry", Integer.class);
        int postingsBefore = jdbc.queryForObject("SELECT count(*) FROM posting", Integer.class);

        mvc.perform(get(location(created)).header("X-Customer-Id", alice.toString())).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM journal_entry", Integer.class)).isEqualTo(entriesBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posting", Integer.class)).isEqualTo(postingsBefore);
    }
}
