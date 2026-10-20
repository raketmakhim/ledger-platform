package com.raketmakhim.ledger.accounting.adapter.persistence;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.raketmakhim.ledger.ExcludeFromJacocoGeneratedReport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The database row for a posting. Its entry_id column is set from JournalEntryEntity. */
@Entity
@Table(name = "posting")
class PostingEntity {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, precision = 24, scale = 4)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    // Hibernate needs a no-argument constructor and is the only caller
    @ExcludeFromJacocoGeneratedReport
    protected PostingEntity() {
    }

    PostingEntity(UUID id, UUID accountId, BigDecimal amount, String currency) {
        this.id = id;
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
    }

    UUID getId() { return id; }
    UUID getAccountId() { return accountId; }
    BigDecimal getAmount() { return amount; }
    String getCurrency() { return currency; }
}
