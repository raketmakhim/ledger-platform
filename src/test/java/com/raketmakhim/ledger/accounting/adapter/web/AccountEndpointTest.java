package com.raketmakhim.ledger.accounting.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.raketmakhim.ledger.TestcontainersConfiguration;
import com.raketmakhim.ledger.accounting.application.IdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The account endpoints through the whole app, against a real Postgres. */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountEndpointTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    IdGenerator ids;

    @Autowired
    ObjectMapper json;

    // a new customer for each test, so the tests cannot see each other's accounts
    private UUID newCaller() {
        return ids.newId();
    }

    private UUID open(UUID caller, String currency) throws Exception {
        MvcResult result = mvc.perform(post("/accounts").header("X-Customer-Id", caller.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"" + currency + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    @Test
    void anOpenedAccountCanBeReadBackByItsOwner() throws Exception {
        UUID caller = newCaller();

        UUID id = open(caller, "GBP");

        mvc.perform(get("/accounts/{id}", id).header("X-Customer-Id", caller.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value("0.00"));
    }

    @Test
    void aYenAccountHasNoDecimalPlaces() throws Exception {
        UUID caller = newCaller();
        UUID id = open(caller, "JPY");

        mvc.perform(get("/accounts/{id}", id).header("X-Customer-Id", caller.toString()))
                .andExpect(jsonPath("$.balance").value("0"));
    }

    @Test
    void theListHoldsOnlyTheCallersAccountsOldestFirst() throws Exception {
        UUID caller = newCaller();
        UUID first = open(caller, "GBP");
        UUID second = open(caller, "EUR");
        open(newCaller(), "GBP");

        mvc.perform(get("/accounts").header("X-Customer-Id", caller.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.toString()))
                .andExpect(jsonPath("$[1].id").value(second.toString()));
    }

    @Test
    void someoneElsesAccountIsNotFound() throws Exception {
        UUID id = open(newCaller(), "GBP");

        mvc.perform(get("/accounts/{id}", id).header("X-Customer-Id", newCaller().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:ledger:problem:account-not-found"));
    }

    @Test
    void aCurrencyTheBankDoesNotSupportIsRefused() throws Exception {
        UUID caller = newCaller();

        mvc.perform(post("/accounts").header("X-Customer-Id", caller.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"CHF\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:ledger:problem:unsupported-currency"));

        mvc.perform(get("/accounts").header("X-Customer-Id", caller.toString()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void withoutACallerNothingIsOpened() throws Exception {
        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"GBP\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aFreshAccountCannotBeTransferredFromUntilItHasMoney() throws Exception {
        UUID caller = newCaller();
        UUID from = open(caller, "GBP");
        UUID to = open(caller, "GBP");

        MvcResult result = mvc.perform(post("/transfers").header("X-Customer-Id", caller.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"" + from + "\",\"to\":\"" + to
                                + "\",\"amount\":\"1.00\",\"currency\":\"GBP\",\"reference\":\"" + from + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("insufficient-funds");
    }
}
