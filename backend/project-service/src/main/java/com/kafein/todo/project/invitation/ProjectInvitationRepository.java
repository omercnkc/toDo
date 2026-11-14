package com.kafein.todo.project.invitation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, Long> {

    /** "Bana gelen davetler" — (invitee_id, status) indeksini kullanır. */
    List<ProjectInvitation> findByInviteeIdAndStatusOrderByCreatedAtDesc(Long inviteeId, InvitationStatus status);

    /** Projede en fazla 50 bekleyen davet kuralı için (docs/06). */
    long countByProjectIdAndStatus(Long projectId, InvitationStatus status);
}
