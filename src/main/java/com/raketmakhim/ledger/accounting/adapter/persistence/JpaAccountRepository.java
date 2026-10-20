package com.raketmakhim.ledger.accounting.adapter.persistence;

import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.raketmakhim.ledger.accounting.application.AccountRepository;
import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;

import jakarta.persistence.EntityManager;

/**
 * Stores accounts in Postgres. Saving an existing account checks its version: if someone else saved it since
 * it was loaded, the save fails with an optimistic-locking exception instead of overwriting their change.
 */
@Repository
public class JpaAccountRepository implements AccountRepository {

    private final EntityManager entityManager;

    public JpaAccountRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return Optional.ofNullable(entityManager.find(AccountEntity.class, id)).map(AccountMapper::toDomain);
    }

    @Override
    public Optional<Account> findClearingAccount(Currency currency) {
        return entityManager.createQuery(
                        "select a from AccountEntity a where a.type = :type and a.currency = :currency", AccountEntity.class)
                .setParameter("type", AccountType.SYSTEM)
                .setParameter("currency", currency.getCurrencyCode())
                .getResultList().stream().findFirst().map(AccountMapper::toDomain);
    }

    @Override
    @Transactional
    public void save(Account account) {
        AccountEntity entity = AccountMapper.toEntity(account);
        if (entityManager.find(AccountEntity.class, account.id()) == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
    }
}
