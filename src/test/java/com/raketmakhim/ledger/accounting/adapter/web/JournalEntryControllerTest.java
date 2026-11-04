package com.raketmakhim.ledger.accounting.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.raketmakhim.ledger.accounting.application.EntryView;
import com.raketmakhim.ledger.accounting.application.JournalEntryService;
import com.raketmakhim.ledger.accounting.application.TransferCommand;
import com.raketmakhim.ledger.accounting.application.TransferService;
import com.raketmakhim.ledger.accounting.domain.journal.EntryType;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntryNotFoundException;
import com.raketmakhim.ledger.accounting.domain.journal.Posting;
import com.raketmakhim.ledger.accounting.domain.money.Money;

/**
 * The HTTP side of GET /journal-entries/{id}. The service is replaced by a mock: who may read what is tested in
 * JournalEntryServiceTest. TransferController is loaded too, to check that both endpoints answer in the same shape.
 */
@WebMvcTest({JournalEntryController.class, TransferController.class})
class JournalEntryControllerTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final String PROBLEM_TYPE = "urn:ledger:problem:";
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    private static final UUID CALLER = new UUID(8, 8);
    private static final UUID ENTRY_ID = new UUID(2, 1);
    private static final UUID FROM = new UUID(1, 1);
    private static final UUID TO = new UUID(1, 2);
    private static final UUID CLEARING = new UUID(1, 3);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JournalEntryService journalEntries;

    @MockitoBean
    TransferService transfers;

    private static Money gbp(String amount) {
        return new Money(new BigDecimal(amount), GBP);
    }

    private static Posting posting(long n, UUID account, String amount) {
        return new Posting(new UUID(3, n), account, gbp(amount));
    }

    private static JournalEntry transfer() {
        return new JournalEntry(ENTRY_ID, EntryType.TRANSFER, "rent-oct", NOW,
                List.of(posting(1, FROM, "-10.00"), posting(2, TO, "10.00")));
    }

    private static EntryView viewOf(JournalEntry entry) {
        return new EntryView(entry.id(), entry.type(), entry.reference(), entry.createdAt(), entry.postings());
    }

    private static MockHttpServletRequestBuilder read(String id) {
        return get("/journal-entries/" + id).header("X-Customer-Id", CALLER.toString());
    }

    // --- success ---

    @Test
    void readingAnEntryReturns200WithItsPostings() throws Exception {
        when(journalEntries.get(CALLER, ENTRY_ID)).thenReturn(viewOf(transfer()));

        mvc.perform(read(ENTRY_ID.toString()))
                .andExpect(status().isOk())
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
    void theControllerAsksTheServiceForTheCallerAndTheIdInTheUrl() throws Exception {
        when(journalEntries.get(CALLER, ENTRY_ID)).thenReturn(viewOf(transfer()));

        mvc.perform(read(ENTRY_ID.toString()));

        verify(journalEntries).get(CALLER, ENTRY_ID);
    }

    @Test
    void aViewWithOnlyOnePostingIsReturnedAsItIs() throws Exception {
        EntryView deposit = new EntryView(ENTRY_ID, EntryType.DEPOSIT, "D-1", NOW, List.of(posting(4, TO, "100.00")));
        when(journalEntries.get(CALLER, ENTRY_ID)).thenReturn(deposit);

        String body = mvc.perform(read(ENTRY_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postings.length()").value(1))
                .andExpect(jsonPath("$.postings[0].accountId").value(TO.toString()))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(CLEARING.toString());
    }

    @Test
    void readingAnEntryGivesTheSameBodyAsCreatingIt() throws Exception {
        JournalEntry entry = transfer();
        when(transfers.transfer(any(TransferCommand.class))).thenReturn(entry);
        when(journalEntries.get(CALLER, ENTRY_ID)).thenReturn(viewOf(entry));

        String created = mvc.perform(post("/transfers").header("X-Customer-Id", CALLER.toString())
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"" + FROM + "\",\"to\":\"" + TO
                                + "\",\"amount\":\"10.00\",\"currency\":\"GBP\",\"reference\":\"rent-oct\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String fetched = mvc.perform(read(ENTRY_ID.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(fetched).isEqualTo(created);
    }

    // --- who is calling ---

    static Stream<Arguments> unidentifiedCallers() {
        return Stream.of(
                Arguments.of("no header", null),
                Arguments.of("empty header", ""),
                Arguments.of("blank header", "   "),
                Arguments.of("not a uuid", "alice"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unidentifiedCallers")
    void aRequestFromAnUnidentifiedCallerReturns401(String name, String header) throws Exception {
        MockHttpServletRequestBuilder request = get("/journal-entries/" + ENTRY_ID);
        if (header != null) {
            request.header("X-Customer-Id", header);
        }

        mvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "unauthenticated"));

        verifyNoInteractions(journalEntries);
    }

    // --- failures ---

    @Test
    void anIdThatIsNotAUuidReturns400AndTheServiceIsNeverCalled() throws Exception {
        mvc.perform(read("not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "invalid-request"));

        verifyNoInteractions(journalEntries);
    }

    @Test
    void anEntryTheCallerCannotSeeReturns404WithAStableType() throws Exception {
        when(journalEntries.get(CALLER, ENTRY_ID)).thenThrow(new JournalEntryNotFoundException(ENTRY_ID));

        mvc.perform(read(ENTRY_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "entry-not-found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void anUnexpectedFailureDoesNotLeakItsMessage() throws Exception {
        when(journalEntries.get(CALLER, ENTRY_ID)).thenThrow(new IllegalStateException("db password is hunter2"));

        String response = mvc.perform(read(ENTRY_ID.toString()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "internal-error"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("hunter2");
    }
}
