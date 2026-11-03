package com.raketmakhim.ledger.accounting.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import jakarta.persistence.PersistenceException;

class ConstraintsTest {

    private static ConstraintViolationException violationOf(String constraint) {
        return new ConstraintViolationException("duplicate", new SQLException("duplicate"), constraint);
    }

    @Test
    void findsTheConstraintWhenItIsTheCauseOfTheFailure() {
        PersistenceException failure = new PersistenceException(violationOf("my_constraint"));

        assertThat(Constraints.violated(failure, "my_constraint")).isTrue();
    }

    @Test
    void ignoresCaseInTheConstraintName() {
        assertThat(Constraints.violated(violationOf("MY_CONSTRAINT"), "my_constraint")).isTrue();
    }

    @Test
    void doesNotMatchAnotherConstraintOrAnotherKindOfFailure() {
        assertThat(Constraints.violated(violationOf("other"), "my_constraint")).isFalse();
        assertThat(Constraints.violated(new PersistenceException("boom"), "my_constraint")).isFalse();
    }
}
