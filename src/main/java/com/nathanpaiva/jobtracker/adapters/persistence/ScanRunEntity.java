package com.nathanpaiva.jobtracker.adapters.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A scan that finished reading the mailbox.
 *
 * <p>One row per completed read, never updated. The value anyone wants is the latest
 * {@code completed_at}, which is what the next run uses to know where to start looking.
 *
 * <p>There is no matching type in the domain, and that is on purpose: this is bookkeeping
 * about the job, not something the business rules have an opinion about. What crosses
 * into the application is an {@code Instant}.
 */
@Entity
@Table(name = "scan_runs")
class ScanRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    /** Hibernate needs this; nothing else should use it. */
    protected ScanRunEntity() {
    }

    static ScanRunEntity completedAt(Instant completedAt) {
        ScanRunEntity entity = new ScanRunEntity();
        entity.completedAt = completedAt;
        return entity;
    }
}
