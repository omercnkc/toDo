package com.kafein.todo.project.invitation;

import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.project.invitation.dto.InvitationResponse;
import com.kafein.todo.project.invitation.dto.InviteRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Proje davetleri", description = "Projenin gönderdiği davetler (OWNER / ADMIN).")
@RestController
@RequestMapping("/projects/{projectId}/invitations")
public class ProjectInvitationController {

    private final InvitationService invitationService;

    public ProjectInvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    /** Davet gönder → 201. Üyelik davet edilen kabul edince oluşur. */
    @Operation(summary = "Davet gönder",
            description = "OWNER / ADMIN. Üyelik, davet edilen kişi kabul edince oluşur.")
    @ApiResponse(responseCode = "201", description = "Davet gönderildi")
    @ApiResponse(responseCode = "400", description = "CANNOT_INVITE_SELF / USER_DISABLED / VALIDATION_ERROR")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / PROJECT_MANAGE_DENIED — üye değil ya da OWNER/ADMIN değil")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND / PROJECT_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "ALREADY_MEMBER / INVITATION_ALREADY_PENDING / INVITATION_RECENTLY_REJECTED (red sonrası 24 saat) / TOO_MANY_PENDING_INVITATIONS (en fazla 50) / PROJECT_ARCHIVED")
    @ApiResponse(responseCode = "503", description = "USER_SERVICE_UNAVAILABLE — auth-service'e ulaşılamadı")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                                     @Valid @RequestBody InviteRequest request) {
        return invitationService.invite(user, projectId, request.userId());
    }

    /** Yeni önce, en fazla 200. {@code status} boşsa hepsi. */
    @Operation(summary = "Projenin davetleri",
            description = "OWNER / ADMIN. `status` boşsa hepsi; yeni önce, en fazla 200.")
    @ApiResponse(responseCode = "200", description = "Davetler")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / PROJECT_MANAGE_DENIED — üye değil ya da OWNER/ADMIN değil")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND")
    @GetMapping
    public List<InvitationResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                                         @RequestParam(required = false) InvitationStatus status) {
        return invitationService.listForProject(user, projectId, status);
    }

    /** Bekleyen daveti iptal et → 204. */
    @Operation(summary = "Daveti iptal et",
            description = "OWNER / ADMIN; yalnızca bekleyen davet. İptal edilen kişi hemen yeniden davet edilebilir.")
    @ApiResponse(responseCode = "204", description = "İptal edildi")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / PROJECT_MANAGE_DENIED — üye değil ya da OWNER/ADMIN değil")
    @ApiResponse(responseCode = "404", description = "INVITATION_NOT_FOUND / PROJECT_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "INVITATION_NOT_PENDING")
    @DeleteMapping("/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                       @PathVariable long invitationId) {
        invitationService.cancel(user, projectId, invitationId);
    }
}
