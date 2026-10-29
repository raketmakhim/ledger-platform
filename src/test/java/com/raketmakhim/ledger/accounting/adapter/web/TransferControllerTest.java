package com.raketmakhim.ledger.accounting.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.raketmakhim.ledger.accounting.application.TransferService;
import com.raketmakhim.ledger.accounting.domain.account.AccountNotActiveException;
import com.raketmakhim.ledger.accounting.domain.account.AccountNotFoundException;
import com.raketmakhim.ledger.accounting.domain.account.AccountStatus;
import com.raketmakhim.ledger.accounting.domain.account.InsufficientFundsException;
import com.raketmakhim.ledger.accounting.domain.journal.DuplicateReferenceException;
import com.raketmakhim.ledger.accounting.domain.journal.EntryType;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.journal.Posting;
import com.raketmakhim.ledger.accounting.domain.money.CurrencyMismatchException;
import com.raketmakhim.ledger.accounting.domain.money.Money;
import com.raketmakhim.ledger.accounting.domain.money.UnsupportedCurrencyException;

/** The HTTP side of POST /transfers. TransferService is replaced by a mock: its own rules are tested in TransferServiceTest. */
@WebMvcTest(TransferController.class)
class TransferControllerTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency USD = Currency.getInstance("USD");
    private static final String PROBLEM_TYPE = "urn:ledger:problem:";

    private static final UUID FROM = new UUID(1, 1);
    private static final UUID TO = new UUID(1, 2);
    private static final UUID ENTRY_ID = new UUID(2, 1);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TransferService transfers;

    private static Money gbp(String amount) {
        return new Money(new BigDecimal(amount), GBP);
    }

    private static String body(String from, String to, String amount, String currency, String reference) {
        return "{\"from\":" + from + ",\"to\":" + to + ",\"amount\":" + amount
                + ",\"currency\":" + currency + ",\"reference\":" + reference + "}";
    }

    private static String validBody() {
        return body("\"" + FROM + "\"", "\"" + TO + "\"", "\"10.00\"", "\"GBP\"", "\"rent-oct\"");
    }

    private ResultActions send(String json) throws Exception {
        return mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static JournalEntry entry() {
        return new JournalEntry(ENTRY_ID, EntryType.TRANSFER, "rent-oct", Instant.parse("2026-10-25T12:00:00Z"),
                List.of(new Posting(new UUID(3, 1), FROM, gbp("10.00").negate()), new Posting(new UUID(3, 2), TO, gbp("10.00"))));
    }

    // --- success ---

    @Test
    void aValidTransferReturns201WithTheEntry() throws Exception {
        when(transfers.transfer(any(), any(), any(), any())).thenReturn(entry());

        send(validBody())
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/journal-entries/" + ENTRY_ID))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(ENTRY_ID.toString()))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.reference").value("rent-oct"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-25T12:00:00Z"))
                .andExpect(jsonPath("$.postings.length()").value(2))
                .andExpect(jsonPath("$.postings[0].accountId").value(FROM.toString()))
                .andExpect(jsonPath("$.postings[0].amount").value("-10.00"))
                .andExpect(jsonPath("$.postings[0].currency").value("GBP"))
                .andExpect(jsonPath("$.postings[1].accountId").value(TO.toString()))
                .andExpect(jsonPath("$.postings[1].amount").value("10.00"));
    }

    @Test
    void theControllerPassesTheRequestOnToTheService() throws Exception {
        when(transfers.transfer(any(), any(), any(), any())).thenReturn(entry());

        send(validBody());

        verify(transfers).transfer(eq(FROM), eq(TO), eq(gbp("10.00")), eq("rent-oct"));
    }

    // --- a request that is the wrong shape: 400, and the service is never called ---

    static Stream<Arguments> badRequests() {
        String from = "\"" + FROM + "\"";
        String to = "\"" + TO + "\"";
        return Stream.of(
                Arguments.of("missing from", body("null", to, "\"10.00\"", "\"GBP\"", "\"r\"")),
                Arguments.of("missing to", body(from, "null", "\"10.00\"", "\"GBP\"", "\"r\"")),
                Arguments.of("missing amount", body(from, to, "null", "\"GBP\"", "\"r\"")),
                Arguments.of("missing currency", body(from, to, "\"10.00\"", "null", "\"r\"")),
                Arguments.of("missing reference", body(from, to, "\"10.00\"", "\"GBP\"", "null")),
                Arguments.of("blank reference", body(from, to, "\"10.00\"", "\"GBP\"", "\"  \"")),
                Arguments.of("from is not a uuid", body("\"abc\"", to, "\"10.00\"", "\"GBP\"", "\"r\"")),
                Arguments.of("amount is not a number", body(from, to, "\"ten\"", "\"GBP\"", "\"r\"")),
                Arguments.of("currency is not a currency code", body(from, to, "\"10.00\"", "\"GB\"", "\"r\"")),
                Arguments.of("currency has no decimal places defined", body(from, to, "\"10.00\"", "\"XXX\"", "\"r\"")),
                Arguments.of("too many decimal places for the currency", body(from, to, "\"10.001\"", "\"GBP\"", "\"r\"")),
                Arguments.of("not json", "{"),
                Arguments.of("empty body", ""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badRequests")
    void aBadlyShapedRequestReturns400(String name, String json) throws Exception {
        send(json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "invalid-request"));

        verifyNoInteractions(transfers);
    }

    // --- a rule broken inside the service: each failure has its own status and stable type ---

    static Stream<Arguments> serviceFailures() {
        return Stream.of(
                Arguments.of(new AccountNotFoundException(FROM), 404, "account-not-found"),
                Arguments.of(new AccountNotActiveException(AccountStatus.FROZEN), 422, "account-not-active"),
                Arguments.of(new InsufficientFundsException(gbp("5.00"), gbp("10.00").negate()), 422, "insufficient-funds"),
                Arguments.of(new CurrencyMismatchException(GBP, USD), 422, "currency-mismatch"),
                Arguments.of(new UnsupportedCurrencyException(USD), 422, "unsupported-currency"),
                // same account, non-positive amount, system account: one type until they get their own exceptions
                Arguments.of(new IllegalArgumentException("Cannot transfer to the same account."), 400, "invalid-request"),
                Arguments.of(new OptimisticLockingFailureException("clash"), 409, "concurrent-update"),
                Arguments.of(new DuplicateReferenceException(EntryType.TRANSFER, "rent-oct"), 409, "duplicate-reference"),
                Arguments.of(new IllegalStateException("db password is hunter2"), 500, "internal-error"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("serviceFailures")
    void aFailureInTheServiceBecomesAProblemResponse(RuntimeException failure, int status, String slug) throws Exception {
        when(transfers.transfer(any(), any(), any(), any())).thenThrow(failure);

        send(validBody())
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + slug))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void insufficientFundsDoesNotRevealTheBalance() throws Exception {
        when(transfers.transfer(any(), any(), any(), any()))
                .thenThrow(new InsufficientFundsException(gbp("5.00"), gbp("10.00").negate()));

        String response = send(validBody()).andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("5.00");
    }

    @Test
    void anUnexpectedFailureDoesNotLeakItsMessage() throws Exception {
        when(transfers.transfer(any(), any(), any(), any())).thenThrow(new IllegalStateException("db password is hunter2"));

        String response = send(validBody()).andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("hunter2");
    }
}
