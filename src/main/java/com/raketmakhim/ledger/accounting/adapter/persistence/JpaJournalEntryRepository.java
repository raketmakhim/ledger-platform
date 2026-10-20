package com.raketmakhim.ledger.accounting.adapter.persistence;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.raketmakhim.ledger.accounting.application.JournalEntryRepository;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;

import jakarta.persistence.EntityManager;

/** Stores journal entries, with their postings, in Postgres. */
@Repository
public class JpaJournalEntryRepository implements JournalEntryRepository {

    private final EntityManager entityManager;

    public JpaJournalEntryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void save(JournalEntry entry) {
        entityManager.persist(JournalEntryMapper.toEntity(entry));
    }
}
