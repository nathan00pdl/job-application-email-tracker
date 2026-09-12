package com.nathanpaiva.jobtracker.adapters.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data repository for {@link ScanRunEntity}.
 *
 * <p>The one query is written out rather than derived from a method name, because it
 * returns a single column rather than an entity. Reading the whole row only to throw
 * everything but one field away would work, and would say less about what is wanted.
 */
interface ScanRunJpaRepository extends JpaRepository<ScanRunEntity, Long> {

    @Query("select max(r.completedAt) from ScanRunEntity r")
    Optional<Instant> findLastCompletedAt();
}
