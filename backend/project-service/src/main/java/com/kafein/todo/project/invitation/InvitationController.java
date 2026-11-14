package com.kafein.todo.project.invitation;

import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.project.invitation.dto.InvitationResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Bana gelen davetler: {@code /api/invitations} ("Davetlerim" sayfası; docs/06). */
@RestController
@RequestMapping("/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    /** Varsayılan: bekleyenler (navbar rozeti de bunu sayar). Yeni önce, en fazla 200. */
    @GetMapping
    public List<InvitationResponse> mine(@AuthenticationPrincipal AuthenticatedUser user,
                                         @RequestParam(defaultValue = "PENDING") InvitationStatus status) {
        return invitationService.listMine(user, status);
    }

    /** Yalnızca davet edilen kişi; kabul edince projeye MEMBER olarak eklenir. */
    @PostMapping("/{id}/accept")
    public InvitationResponse accept(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return invitationService.accept(user, id);
    }

    /** Yalnızca davet edilen kişi. */
    @PostMapping("/{id}/reject")
    public InvitationResponse reject(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return invitationService.reject(user, id);
    }
}
