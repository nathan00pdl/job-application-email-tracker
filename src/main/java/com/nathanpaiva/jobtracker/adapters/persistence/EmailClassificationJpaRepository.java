package com.nathanpaiva.jobtracker.adapters.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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

    List<EmailClassificationEntity> findBySheetSyncedAtIsNullOrderByReceivedAtAsc();

    /**
     * Written out because this one changes rows rather than reading them, and Spring
     * cannot derive that from a method name.
     *
     * <p>{@code @Modifying} tells Spring the query writes, so it uses the right JDBC
     * call and clears the persistence context afterwards — without it, entities already
     * loaded in this transaction would still show the old value.
     */
    @Modifying
    @Query("update EmailClassificationEntity e set e.sheetSyncedAt = :syncedAt "
            + "where e.gmailMessageId in :gmailMessageIds")
    int markSynced(Collection<String> gmailMessageIds, Instant syncedAt);
}
