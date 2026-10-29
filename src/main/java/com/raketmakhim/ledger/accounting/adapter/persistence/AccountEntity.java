package com.raketmakhim.ledger.accounting.adapter.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.raketmakhim.ledger.ExcludeFromJacocoGeneratedReport;
import com.raketmakhim.ledger.accounting.domain.account.AccountStatus;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** The database row for an account. Only the mapper and the repository use it: the domain never sees it. */
@Entity
@Table(name = "account")
class AccountEntity {

    @Id
    private UUID id;

    // null for the bank's own accounts
    @Column(name = "owner_id")
    private UUID ownerId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false, precision = 24, scale = 4)
    private BigDecimal balance;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Hibernate needs a no-argument constructor and is the only caller
    @ExcludeFromJacocoGeneratedReport
    protected AccountEntity() {
    }

    AccountEntity(UUID id, UUID ownerId, String currency, AccountType type, AccountStatus status, BigDecimal balance,
                  long version, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.currency = currency;
        this.type = type;
        this.status = status;
        this.balance = balance;
        this.version = version;
        this.createdAt = createdAt;
    }

    UUID getId() { return id; }
    UUID getOwnerId() { return ownerId; }
    String getCurrency() { return currency; }
    AccountType getType() { return type; }
    AccountStatus getStatus() { return status; }
    BigDecimal getBalance() { return balance; }
    long getVersion() { return version; }
    Instant getCreatedAt() { return createdAt; }
}
