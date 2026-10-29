package com.raketmakhim.ledger.accounting.adapter.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.raketmakhim.ledger.accounting.application.AccountService;
import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountNotFoundException;
import com.raketmakhim.ledger.accounting.domain.account.AccountStatus;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;
import com.raketmakhim.ledger.accounting.domain.money.Money;
import com.raketmakhim.ledger.accounting.domain.money.UnsupportedCurrencyException;

/** The HTTP side of the account endpoints. AccountService is replaced by a mock: its rules are tested in AccountServiceTest. */
@WebMvcTest(AccountController.class)
class AccountControllerTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency CHF = Currency.getInstance("CHF");
    private static final String PROBLEM_TYPE = "urn:ledger:problem:";

    private static final UUID CALLER = new UUID(8, 8);
    private static final UUID ACCOUNT_ID = new UUID(1, 1);
    private static final UUID OTHER_ACCOUNT_ID = new UUID(1, 2);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AccountService accounts;

    private static Account account(UUID id, String balance) {
        return new Account(id, CALLER, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE,
                new Money(new BigDecimal(balance), GBP), 3, Instant.parse("2026-10-25T12:00:00Z"));
    }

    private static MockHttpServletRequestBuilder asCaller(MockHttpServletRequestBuilder request) {
        return request.header("X-Customer-Id", CALLER.toString());
    }

    private static MockHttpServletRequestBuilder openAccount(String json) {
        return asCaller(post("/accounts")).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    // --- opening an account ---

    @Test
    void openingAnAccountReturns201WithTheAccountAndItsLocation() throws Exception {
        when(accounts.open(CALLER, GBP)).thenReturn(account(ACCOUNT_ID, "0.00"));

        mvc.perform(openAccount("{\"currency\":\"GBP\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/accounts/" + ACCOUNT_ID))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value("0.00"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-25T12:00:00Z"));
    }

    @Test
    void theResponseDoesNotShowTheOwnerOrTheInternalVersion() throws Exception {
        when(accounts.open(CALLER, GBP)).thenReturn(account(ACCOUNT_ID, "0.00"));

        mvc.perform(openAccount("{\"currency\":\"GBP\"}"))
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    static Stream<Arguments> badOpenRequests() {
        return Stream.of(
                Arguments.of("no currency", "{}"),
                Arguments.of("null currency", "{\"currency\":null}"),
                Arguments.of("empty currency", "{\"currency\":\"\"}"),
                Arguments.of("blank currency", "{\"currency\":\"   \"}"),
                Arguments.of("not a currency code", "{\"currency\":\"GB\"}"),
                Arguments.of("lower case", "{\"currency\":\"gbp\"}"),
                Arguments.of("not json", "{"),
                Arguments.of("empty body", ""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badOpenRequests")
    void aBadlyShapedRequestReturns400AndOpensNothing(String name, String json) throws Exception {
        mvc.perform(openAccount(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "invalid-request"));

        verifyNoInteractions(accounts);
    }

    @Test
    void aCurrencyTheBankDoesNotSupportReturns422() throws Exception {
        when(accounts.open(CALLER, CHF)).thenThrow(new UnsupportedCurrencyException(CHF));

        mvc.perform(openAccount("{\"currency\":\"CHF\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "unsupported-currency"));
    }

    // --- looking at one account ---

    @Test
    void anAccountOfTheCallerIsReturned() throws Exception {
        when(accounts.get(CALLER, ACCOUNT_ID)).thenReturn(account(ACCOUNT_ID, "12.50"));

        mvc.perform(asCaller(get("/accounts/{id}", ACCOUNT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value("12.50"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-25T12:00:00Z"));
    }

    @Test
    void anAccountThatIsMissingOrNotTheCallersReturns404() throws Exception {
        when(accounts.get(CALLER, ACCOUNT_ID)).thenThrow(new AccountNotFoundException(ACCOUNT_ID));

        mvc.perform(asCaller(get("/accounts/{id}", ACCOUNT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "account-not-found"));
    }

    @Test
    void anIdThatIsNotAUuidReturns400() throws Exception {
        mvc.perform(asCaller(get("/accounts/not-a-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "invalid-request"));

        verifyNoInteractions(accounts);
    }

    // --- listing ---

    @Test
    void theListHoldsTheCallersAccounts() throws Exception {
        when(accounts.list(CALLER)).thenReturn(List.of(account(ACCOUNT_ID, "12.50"), account(OTHER_ACCOUNT_ID, "0.00")));

        mvc.perform(asCaller(get("/accounts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$[0].balance").value("12.50"))
                .andExpect(jsonPath("$[1].id").value(OTHER_ACCOUNT_ID.toString()));
    }

    @Test
    void aCallerWithNoAccountsGetsAnEmptyList() throws Exception {
        when(accounts.list(CALLER)).thenReturn(List.of());

        mvc.perform(asCaller(get("/accounts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- every account endpoint needs to know who is calling ---

    static Stream<Arguments> requestsWithoutACaller() {
        return Stream.of(
                Arguments.of("open an account", post("/accounts")),
                Arguments.of("view an account", get("/accounts/" + ACCOUNT_ID)),
                Arguments.of("list accounts", get("/accounts")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestsWithoutACaller")
    void withoutACallerEveryEndpointReturns401(String name, MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"GBP\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "unauthenticated"));

        verifyNoInteractions(accounts);
    }
}
