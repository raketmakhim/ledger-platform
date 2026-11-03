package com.raketmakhim.ledger.accounting.adapter.persistence;

import org.hibernate.exception.ConstraintViolationException;

/** Finds out which database constraint a failed statement broke. */
final class Constraints {

    private Constraints() {
    }

    static boolean violated(Throwable failure, String constraintName) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
