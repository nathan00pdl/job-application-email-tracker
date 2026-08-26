package com.nathanpaiva.jobtracker.adapters.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for the entity.
 *
 * <p>Spring writes the implementation at startup: {@code existsByGmailMessageId} is
 * turned into a query by reading the method name, so there is no SQL to keep in sync
 * with the field it filters on.
 *
 * <p>This is a second interface next to {@code PersistencePort}, and they are not the
 * same thing. This one speaks JPA and entities and belongs to Spring; the port speaks
 * the domain and belongs to the application. The adapter is the translation between
 * them, and it is why Spring Data never appears anywhere else in the codebase.
 */
interface EmailClassificationJpaRepository extends JpaRepository<EmailClassificationEntity, Long> {

    boolean existsByGmailMessageId(String gmailMessageId);
}
