package com.kafein.todo.project.invitation;

import com.kafein.todo.project.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * Projeye davet. Üyelik davet edilen kabul edince oluşur (docs/04). Durum yalnızca
 * {@code PENDING}'den bir kez değişir; yanıtlanmış davet tekrar yanıtlanamaz.
 * <p>
 * Tablodaki {@code pending_flag} üretilmiş kolonu (tek bekleyen davet kuralı) burada eşlenmez;
 * değeri MySQL hesaplar.
 */
@Entity
@Table(name = "project_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA için
public class ProjectInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(name = "invitee_id", nullable = false, updatable = false)
    private Long inviteeId;

    @Column(name = "inviter_id", nullable = false, updatable = false)
    private Long inviterId;

    @Convert(converter = InvitationStatus.JpaConverter.class)
    @Column(nullable = false)
    private InvitationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    public ProjectInvitation(Project project, long inviteeId, long inviterId) {
        this.project = Objects.requireNonNull(project, "project");
        this.inviteeId = inviteeId;
        this.inviterId = inviterId;
        this.status = InvitationStatus.PENDING;
    }

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    public void accept(Instant now) {
        respond(InvitationStatus.ACCEPTED, now);
    }

    public void reject(Instant now) {
        respond(InvitationStatus.REJECTED, now);
    }

    public void cancel(Instant now) {
        respond(InvitationStatus.CANCELLED, now);
    }

    /** Servis katmanı önce {@link #isPending()} ile anlamlı hata (409) üretir; bu son savunma hattı. */
    private void respond(InvitationStatus newStatus, Instant now) {
        if (!isPending()) {
            throw new IllegalStateException("Davet zaten yanıtlanmış: " + status);
        }
        this.status = newStatus;
        this.respondedAt = Objects.requireNonNull(now, "now");
    }
}
