package com.raketmakhim.ledger.accounting.domain.account;

public enum AccountType {
    CUSTOMER(false),
    /** The bank's own account, such as clearing. May have a negative balance. */
    SYSTEM(true);

    private final boolean allowsNegativeBalance;

    AccountType(boolean allowsNegativeBalance){
        this.allowsNegativeBalance = allowsNegativeBalance;
    }

    /** The one place that says which accounts may go negative. V1__init.sql has a matching CHECK on account. */
    public boolean allowsNegativeBalance(){
        return allowsNegativeBalance;
    }
}
