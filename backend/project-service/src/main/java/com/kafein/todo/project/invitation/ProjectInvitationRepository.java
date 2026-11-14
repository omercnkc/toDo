package com.kafein.todo.project.invitation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, Long> {

    /** "Bana gelen davetler" — (invitee_id, status) indeksini kullanır. */
    List<ProjectInvitation> findByInviteeIdAndStatusOrderByCreatedAtDesc(Long inviteeId, InvitationStatus status);

    /** Projede en fazla 50 bekleyen davet kuralı için (docs/06). */
    long countByProjectIdAndStatus(Long projectId, InvitationStatus status);

    boolean existsByProjectIdAndInviteeIdAndStatus(Long projectId, Long inviteeId, InvitationStatus status);

    /** Redden sonra 24 saat yeniden davet yok kuralı. */
    boolean existsByProjectIdAndInviteeIdAndStatusAndRespondedAtAfter(Long projectId, Long inviteeId,
                                                                     InvitationStatus status, Instant since);

    /**
     * Kabul/red/iptal için satırı kilitler ({@code SELECT ... FOR UPDATE}): aynı anda gelen kabul ve iptal
     * birbirini ezmez, ikincisi ilki bitince güncel durumu görür.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ProjectInvitation i join fetch i.project where i.id = :id")
    Optional<ProjectInvitation> findForUpdate(@Param("id") Long id);

    /** Projenin davetleri, yeni önce. {@code status} null → hepsi. */
    @Query("""
            select i from ProjectInvitation i join fetch i.project
             where i.project.id = :projectId and (:status is null or i.status = :status)
             order by i.createdAt desc, i.id desc""")
    List<ProjectInvitation> findForProject(@Param("projectId") Long projectId,
                                           @Param("status") InvitationStatus status, Pageable limit);

    /** Kullanıcıya gelen davetler, yeni önce. */
    @Query("""
            select i from ProjectInvitation i join fetch i.project
             where i.inviteeId = :inviteeId and i.status = :status
             order by i.createdAt desc, i.id desc""")
    List<ProjectInvitation> findForInvitee(@Param("inviteeId") Long inviteeId,
                                           @Param("status") InvitationStatus status, Pageable limit);
}
