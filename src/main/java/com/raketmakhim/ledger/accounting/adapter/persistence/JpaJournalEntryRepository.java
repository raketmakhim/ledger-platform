package com.raketmakhim.ledger.accounting.adapter.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.raketmakhim.ledger.accounting.application.JournalEntryRepository;
import com.raketmakhim.ledger.accounting.domain.journal.DuplicateReferenceException;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

/** Stores journal entries, with their postings, in Postgres. */
@Repository
public class JpaJournalEntryRepository implements JournalEntryRepository {

    // named in V1__init.sql
    private static final String UNIQUE_REFERENCE = "journal_entry_type_reference_uq";

    private final EntityManager entityManager;

    public JpaJournalEntryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void save(JournalEntry entry) {
        entityManager.persist(JournalEntryMapper.toEntity(entry));
        try {
            // send the insert now: otherwise a duplicate reference only fails at commit, too late to report clearly
            entityManager.flush();
        } catch (PersistenceException e) {
            if (isDuplicateReference(e)) {
                throw new DuplicateReferenceException(entry.type(), entry.reference());
            }
            throw e;
        }
    }

    private static boolean isDuplicateReference(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException violation
                    && UNIQUE_REFERENCE.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
