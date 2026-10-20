package com.raketmakhim.ledger.accounting.adapter.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.raketmakhim.ledger.ExcludeFromJacocoGeneratedReport;
import com.raketmakhim.ledger.accounting.domain.journal.EntryType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** The database row for a journal entry, with its posting rows. Saving the entry saves the postings. */
@Entity
@Table(name = "journal_entry")
class JournalEntryEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType type;

    @Column(nullable = false, length = 36)
    private String reference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "entry_id", nullable = false)
    private List<PostingEntity> postings;

    // Hibernate needs a no-argument constructor and is the only caller
    @ExcludeFromJacocoGeneratedReport
    protected JournalEntryEntity() {
    }

    JournalEntryEntity(UUID id, EntryType type, String reference, Instant createdAt, List<PostingEntity> postings) {
        this.id = id;
        this.type = type;
        this.reference = reference;
        this.createdAt = createdAt;
        this.postings = postings;
    }

    UUID getId() { return id; }
    EntryType getType() { return type; }
    String getReference() { return reference; }
    Instant getCreatedAt() { return createdAt; }
    List<PostingEntity> getPostings() { return postings; }
}
