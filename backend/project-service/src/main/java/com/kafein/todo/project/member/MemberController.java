package com.kafein.todo.project.member;

import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.project.member.dto.MemberResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** {@code /api/projects/{id}/members} (docs/06). Üyeler doğrudan eklenmez; davetle gelir (3-D). */
@Tag(name = "Proje üyeleri", description = "Üyeler davetle gelir; buradan listelenir ve çıkarılır.")
@RestController
@RequestMapping("/projects/{projectId}/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    /** Üye / ADMIN. */
    @Operation(summary = "Proje üyeleri",
            description = "Üye / ADMIN. Önce OWNER, sonra katılma sırası; sayfasız. auth-service'e ulaşılamazsa liste yine döner, `email`, `fullName`, `enabled` alanları `null` olur.")
    @ApiResponse(responseCode = "200", description = "Üyeler")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED — projenin üyesi değil")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND")
    @GetMapping
    public List<MemberResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId) {
        return memberService.list(user, projectId);
    }

    /** OWNER / ADMIN; OWNER çıkarılamaz. */
    @Operation(summary = "Üyeyi çıkar",
            description = "OWNER / ADMIN. Çıkarılan kişinin bu projedeki görev atamaları outbox ile asenkron boşaltılır.")
    @ApiResponse(responseCode = "204", description = "Çıkarıldı")
    @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / PROJECT_MANAGE_DENIED — üye değil ya da OWNER/ADMIN değil")
    @ApiResponse(responseCode = "404", description = "MEMBER_NOT_FOUND / PROJECT_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "OWNER_CANNOT_BE_REMOVED / PROJECT_ARCHIVED")
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                       @PathVariable long userId) {
        memberService.remove(user, projectId, userId);
    }
}
