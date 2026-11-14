package com.kafein.todo.project.member;

import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.project.member.dto.MemberResponse;
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
@RestController
@RequestMapping("/projects/{projectId}/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    /** Üye / ADMIN. */
    @GetMapping
    public List<MemberResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId) {
        return memberService.list(user, projectId);
    }

    /** OWNER / ADMIN; OWNER çıkarılamaz. */
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                       @PathVariable long userId) {
        memberService.remove(user, projectId, userId);
    }
}
