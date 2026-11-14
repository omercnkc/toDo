package com.kafein.todo.project.invitation.dto;

import com.kafein.todo.project.client.UserSummary;
import com.kafein.todo.project.invitation.InvitationStatus;
import com.kafein.todo.project.invitation.ProjectInvitation;

import java.time.Instant;
import java.util.Map;

/**
 * Davet (docs/06). Kişilerin ad/e-postası auth-service'ten gelir; ulaşılamazsa null olur (yalnızca id kalır).
 */
public record InvitationResponse(Long id, InvitationStatus status, Instant createdAt, Instant respondedAt,
                                 ProjectRef project, Person inviter, Person invitee) {

    public record ProjectRef(Long id, String name) {
    }

    public record Person(Long id, String fullName, String email) {

        static Person of(Long id, Map<Long, UserSummary> users) {
            UserSummary user = users.get(id);
            return new Person(id, user != null ? user.fullName() : null, user != null ? user.email() : null);
        }
    }

    public static InvitationResponse from(ProjectInvitation invitation, Map<Long, UserSummary> users) {
        return new InvitationResponse(invitation.getId(), invitation.getStatus(), invitation.getCreatedAt(),
                invitation.getRespondedAt(),
                new ProjectRef(invitation.getProject().getId(), invitation.getProject().getName()),
                Person.of(invitation.getInviterId(), users),
                Person.of(invitation.getInviteeId(), users));
    }
}
