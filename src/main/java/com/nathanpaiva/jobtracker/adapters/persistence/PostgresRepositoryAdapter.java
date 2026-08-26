package com.nathanpaiva.jobtracker.adapters.persistence;

import org.springframework.stereotype.Component;

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
}
