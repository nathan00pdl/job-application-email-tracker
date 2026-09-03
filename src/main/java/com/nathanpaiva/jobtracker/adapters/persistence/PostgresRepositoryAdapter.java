package com.nathanpaiva.jobtracker.adapters.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nathanpaiva.jobtracker.domain.EmailClassification;
import com.nathanpaiva.jobtracker.ports.PersistencePort;

/**
 * Stores classifications in PostgreSQL.
 *
 * <p>The whole class is a translation layer: it takes domain objects, hands entities to
 * Spring Data, and turns what comes back into domain objects again. There is no logic
 * here on purpose. Anything worth a decision belongs in the domain, where it can be
 * tested without a database.
 *
 * <p>Package-private, like the entity and the repository. Spring builds it and injects
 * it wherever a {@code PersistencePort} is asked for, so no caller ever names this class
 * and no caller can accidentally depend on the fact that storage happens to be JPA.
 */
@Component
class PostgresRepositoryAdapter implements PersistencePort {

    private final EmailClassificationJpaRepository repository;

    PostgresRepositoryAdapter(EmailClassificationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(EmailClassification classification) {
        repository.save(EmailClassificationEntity.fromDomain(classification));
    }

    @Override
    public boolean existsByGmailMessageId(String gmailMessageId) {
        return repository.existsByGmailMessageId(gmailMessageId);
    }

    @Override
    public List<EmailClassification> findNotSyncedToSpreadsheet() {
        return repository.findBySheetSyncedAtIsNullOrderByReceivedAtAsc().stream()
                .map(EmailClassificationEntity::toDomain)
                .toList();
    }

    /**
     * {@code @Transactional} is needed because the query writes. Spring Data opens a
     * transaction around each of its own methods, but a modifying query called from
     * outside one is rejected rather than quietly running without.
     */
    @Override
    @Transactional
    public void markSyncedToSpreadsheet(Collection<String> gmailMessageIds, Instant syncedAt) {
        if (gmailMessageIds.isEmpty()) {
            return;
        }
        repository.markSynced(gmailMessageIds, syncedAt);
    }
}
