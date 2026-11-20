package com.kafein.todo.project.invitation;

import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.project.invitation.dto.InvitationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Bana gelen davetler: {@code /api/invitations} ("Davetlerim" sayfası; docs/06). */
@Tag(name = "Davetlerim", description = "Bana gelen proje davetleri.")
@RestController
@RequestMapping("/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    /** Varsayılan: bekleyenler (navbar rozeti de bunu sayar). Yeni önce, en fazla 200. */
    @Operation(summary = "Bana gelen davetler",
            description = "Varsayılan `status=PENDING`; yeni önce, en fazla 200.")
    @ApiResponse(responseCode = "200", description = "Davetler")
    @GetMapping
    public List<InvitationResponse> mine(@AuthenticationPrincipal AuthenticatedUser user,
                                         @RequestParam(defaultValue = "PENDING") InvitationStatus status) {
        return invitationService.listMine(user, status);
    }

    /** Yalnızca davet edilen kişi; kabul edince projeye MEMBER olarak eklenir. */
    @Operation(summary = "Daveti kabul et",
            description = "Yalnızca davet edilen kişi; projeye MEMBER olarak eklenir. Arşivdeki projenin daveti kabul edilemez (reddedilebilir).")
    @ApiResponse(responseCode = "200", description = "Güncel davet")
    @ApiResponse(responseCode = "403", description = "INVITATION_FORBIDDEN — davet başkasına (ADMIN dahil)")
    @ApiResponse(responseCode = "404", description = "INVITATION_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "INVITATION_NOT_PENDING / PROJECT_ARCHIVED")
    @PostMapping("/{id}/accept")
    public InvitationResponse accept(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return invitationService.accept(user, id);
    }

    /** Yalnızca davet edilen kişi. */
    @Operation(summary = "Daveti reddet",
            description = "Yalnızca davet edilen kişi. Aynı projeden 24 saat yeniden davet gönderilemez.")
    @ApiResponse(responseCode = "200", description = "Güncel davet")
    @ApiResponse(responseCode = "403", description = "INVITATION_FORBIDDEN — davet başkasına (ADMIN dahil)")
    @ApiResponse(responseCode = "404", description = "INVITATION_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "INVITATION_NOT_PENDING")
    @PostMapping("/{id}/reject")
    public InvitationResponse reject(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return invitationService.reject(user, id);
    }
}
