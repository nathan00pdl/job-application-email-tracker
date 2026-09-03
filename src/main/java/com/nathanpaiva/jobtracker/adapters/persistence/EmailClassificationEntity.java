package com.nathanpaiva.jobtracker.adapters.persistence;

import java.time.Instant;

import com.nathanpaiva.jobtracker.domain.EmailClassification;
import com.nathanpaiva.jobtracker.domain.UpdateType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * How a classification is stored in the {@code email_classifications} table.
 *
 * <p>This is a second class describing the same thing as {@link EmailClassification},
 * and that is the point. The domain record stays free of {@code @Entity},
 * {@code @Column} and Hibernate's need for a no-argument constructor and mutable
 * fields; all of that lives here, outside, where changing the database cannot reach
 * the business rules.
 *
 * <p>It is package-private, together with the repository and the adapter, so nothing
 * outside this package can hold a reference to it. The only way in is
 * {@code PersistencePort}.
 *
 * <p>It carries four columns the domain record does not: {@code id} and
 * {@code created_at} belong to the database, {@code manual_status} is filled in by hand
 * in the spreadsheet, and {@code sheet_synced_at} is set by the spreadsheet sync.
 */
@Entity
@Table(name = "email_classifications")
class EmailClassificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gmail_message_id", nullable = false, unique = true, length = 64)
    private String gmailMessageId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "sender_domain", nullable = false)
    private String senderDomain;

    @Column(name = "platform")
    private String platform;

    @Column(name = "company")
    private String company;

    @Column(name = "role_title")
    private String roleTitle;

    /**
     * Stored as text, not as a native database enum. {@code EnumType.STRING} writes the
     * constant name, so the column stays readable and a new category never needs a
     * migration. {@code EnumType.ORDINAL} would store a number, and reordering the enum
     * would then silently change the meaning of every existing row.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "update_type", nullable = false, length = 50)
    private UpdateType updateType;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "is_urgent", nullable = false)
    private boolean urgent;

    @Column(name = "manual_status", length = 50)
    private String manualStatus;

    @Column(name = "sheet_synced_at")
    private Instant sheetSyncedAt;

    /** Set by the database default, never by this code, hence not insertable. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    EmailClassification toDomain() {
        return new EmailClassification(
                gmailMessageId, receivedAt, senderDomain, platform, company, roleTitle,
                updateType, summary, urgent);
    }

    /** Required by Hibernate, which builds entities by reflection. */
    protected EmailClassificationEntity() {
    }

    static EmailClassificationEntity fromDomain(EmailClassification classification) {
        EmailClassificationEntity entity = new EmailClassificationEntity();
        entity.gmailMessageId = classification.gmailMessageId();
        entity.receivedAt = classification.receivedAt();
        entity.senderDomain = classification.senderDomain();
        entity.platform = classification.platform();
        entity.company = classification.company();
        entity.roleTitle = classification.roleTitle();
        entity.updateType = classification.updateType();
        entity.summary = classification.summary();
        entity.urgent = classification.urgent();
        return entity;
    }
}
