package com.raketmakhim.ledger.accounting.adapter.web;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.raketmakhim.ledger.accounting.domain.account.AccountNotActiveException;
import com.raketmakhim.ledger.accounting.domain.account.AccountNotFoundException;
import com.raketmakhim.ledger.accounting.domain.account.InsufficientFundsException;
import com.raketmakhim.ledger.accounting.domain.money.CurrencyMismatchException;
import com.raketmakhim.ledger.accounting.domain.money.UnsupportedCurrencyException;

/**
 * Turns every failure into an RFC 9457 problem. The "type" is a fixed URN per kind of failure, so clients can
 * rely on it. Spring's own errors (wrong method, wrong media type, ...) keep their usual status.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static ProblemDetail problem(HttpStatus status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:ledger:problem:" + slug));
        problem.setTitle(title);
        return problem;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail accountNotFound(AccountNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "account-not-found", "Account not found", e.getMessage());
    }

    @ExceptionHandler(AccountNotActiveException.class)
    ProblemDetail accountNotActive(AccountNotActiveException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "account-not-active", "Account not active", e.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail insufficientFunds(InsufficientFundsException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient-funds", "Insufficient funds", e.getMessage());
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    ProblemDetail currencyMismatch(CurrencyMismatchException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "currency-mismatch", "Currency mismatch", e.getMessage());
    }

    @ExceptionHandler(UnsupportedCurrencyException.class)
    ProblemDetail unsupportedCurrency(UnsupportedCurrencyException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "unsupported-currency", "Unsupported currency", e.getMessage());
    }

    // shortcut: the domain throws plain IllegalArgumentException for several different problems, so they share
    // one type. Give each its own exception before clients depend on telling them apart.
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", e.getMessage());
    }

    // Thrown when the retries are used up. The message is not sent: it names internal classes and ids.
    @ExceptionHandler(ConcurrencyFailureException.class)
    ProblemDetail concurrentUpdate(ConcurrencyFailureException e) {
        return problem(HttpStatus.CONFLICT, "concurrent-update", "Try again",
                "Other requests kept changing the same accounts. Try again.");
    }

    // The message is not sent to the client: it may contain internals.
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error",
                "Something went wrong. Try again later.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return invalidShape(e, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return invalidShape(e, headers, status, request);
    }

    private ResponseEntity<Object> invalidShape(Exception e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request",
                "The body must be JSON with from, to, amount, currency and reference.");
        return handleExceptionInternal(e, problem, headers, status, request);
    }
}
