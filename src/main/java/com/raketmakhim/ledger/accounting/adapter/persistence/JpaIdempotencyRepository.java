package com.raketmakhim.ledger.accounting.adapter.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.raketmakhim.ledger.accounting.application.IdempotencyRecord;
import com.raketmakhim.ledger.accounting.application.IdempotencyRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

/** Stores the first result for each caller's key in Postgres. */
@Repository
public class JpaIdempotencyRepository implements IdempotencyRepository {

    // named in V1__init.sql
    private static final String PRIMARY_KEY = "idempotency_record_pk";

    private final EntityManager entityManager;

    public JpaIdempotencyRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // one transaction, because the entry's postings are loaded lazily while it is converted
    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> find(UUID callerId, String key) {
        List<?> rows = entityManager.createNativeQuery(
                        "select fingerprint, entry_id, created_at from idempotency_record "
                                + "where caller_id = ?1 and idempotency_key = ?2")
                .setParameter(1, callerId)
                .setParameter(2, key)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] columns = (Object[]) rows.get(0);
        JournalEntryEntity entry = entityManager.find(JournalEntryEntity.class, (UUID) columns[1]);
        return Optional.of(new IdempotencyRecord(callerId, key, (String) columns[0],
                JournalEntryMapper.toDomain(entry), toInstant(columns[2])));
    }

    @Override
    @Transactional
    public void save(IdempotencyRecord record) {
        try {
            entityManager.createNativeQuery(
                            "insert into idempotency_record (caller_id, idempotency_key, fingerprint, entry_id, created_at) "
                                    + "values (?1, ?2, ?3, ?4, ?5)")
                    .setParameter(1, record.callerId())
                    .setParameter(2, record.key())
                    .setParameter(3, record.fingerprint())
                    .setParameter(4, record.entry().id())
                    .setParameter(5, record.createdAt())
                    .executeUpdate();
        } catch (PersistenceException e) {
            if (Constraints.violated(e, PRIMARY_KEY)) {
                // the other request has now finished, so running this one again finds its record
                throw new ConcurrencyFailureException("Idempotency key claimed by another request", e);
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public void release(UUID callerId, String key) {
        entityManager.createNativeQuery("delete from idempotency_record where caller_id = ?1 and idempotency_key = ?2")
                .setParameter(1, callerId)
                .setParameter(2, key)
                .executeUpdate();
    }

    @Override
    @Transactional
    public int deleteExpired(Instant cutoff) {
        return entityManager.createNativeQuery("delete from idempotency_record where created_at <= ?1")
                .setParameter(1, cutoff)
                .executeUpdate();
    }

    // a native query hands back whichever date type the driver chose
    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime time) {
            return time.toInstant();
        }
        return ((Timestamp) value).toInstant();
    }
}
