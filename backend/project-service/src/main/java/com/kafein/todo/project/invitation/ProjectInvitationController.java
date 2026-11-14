package com.kafein.todo.project.invitation;

import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.project.invitation.dto.InvitationResponse;
import com.kafein.todo.project.invitation.dto.InviteRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Projenin gönderdiği davetler: {@code /api/projects/{id}/invitations} (OWNER / ADMIN; docs/06). */
@RestController
@RequestMapping("/projects/{projectId}/invitations")
public class ProjectInvitationController {

    private final InvitationService invitationService;

    public ProjectInvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    /** Davet gönder → 201. Üyelik davet edilen kabul edince oluşur. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                                     @Valid @RequestBody InviteRequest request) {
        return invitationService.invite(user, projectId, request.userId());
    }

    /** Yeni önce, en fazla 200. {@code status} boşsa hepsi. */
    @GetMapping
    public List<InvitationResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                                         @RequestParam(required = false) InvitationStatus status) {
        return invitationService.listForProject(user, projectId, status);
    }

    /** Bekleyen daveti iptal et → 204. */
    @DeleteMapping("/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                       @PathVariable long invitationId) {
        invitationService.cancel(user, projectId, invitationId);
    }
}
