package com.kafein.todo.project.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Transactional Outbox kaydı. İş verisiyle aynı transaction'da {@code PENDING} yazılır; {@link OutboxRelay} işler.
 * Durum geçişleri: {@code PENDING → SENT} veya {@code PENDING → (yeniden dene) → … → FAILED}.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA için
public class OutboxEvent {

    static final String AGGREGATE_PROJECT = "PROJECT";
    static final String PAYLOAD_USER_ID = "userId";
    static final int LAST_ERROR_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private Long aggregateId;

    @Convert(converter = OutboxEventType.JpaConverter.class)
    @Column(name = "event_type", nullable = false, updatable = false)
    private OutboxEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private Map<String, Object> payload;

    @Convert(converter = OutboxStatus.JpaConverter.class)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = LAST_ERROR_MAX)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    private OutboxEvent(long projectId, OutboxEventType eventType, Map<String, Object> payload, Instant now) {
        this.aggregateType = AGGREGATE_PROJECT;
        this.aggregateId = projectId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public static OutboxEvent projectDeleted(long projectId, Instant now) {
        return new OutboxEvent(projectId, OutboxEventType.PROJECT_DELETED, null, now);
    }

    public static OutboxEvent memberRemoved(long projectId, long userId, Instant now) {
        return new OutboxEvent(projectId, OutboxEventType.MEMBER_REMOVED, Map.of(PAYLOAD_USER_ID, userId), now);
    }

    /** MEMBER_REMOVED olayındaki kullanıcı id'si. */
    public long payloadUserId() {
        Object value = payload == null ? null : payload.get(PAYLOAD_USER_ID);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Outbox " + id + ": payload'da userId yok");
        }
        return number.longValue();
    }

    /** Relay kaydı sahiplendi: işlem sürerken (veya relay çökerse) bu süreye kadar başkası almaz. */
    void claimUntil(Instant leaseEnd) {
        this.nextAttemptAt = leaseEnd;
    }

    void markSent(Instant now) {
        this.status = OutboxStatus.SENT;
        this.processedAt = now;
        this.lastError = null;
    }

    /**
     * Başarısız deneme. {@code giveUp} ise {@code FAILED}, değilse {@code retryAt}'te yeniden denenir.
     */
    void markAttemptFailed(String error, Instant retryAt, boolean giveUp, Instant now) {
        this.attempts++;
        this.lastError = truncate(error);
        if (giveUp) {
            this.status = OutboxStatus.FAILED;
            this.processedAt = now;
        } else {
            this.nextAttemptAt = retryAt;
        }
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= LAST_ERROR_MAX ? error : error.substring(0, LAST_ERROR_MAX);
    }
}
