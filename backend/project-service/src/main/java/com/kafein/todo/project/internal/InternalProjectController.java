package com.kafein.todo.project.internal;

import com.kafein.todo.common.error.ApiException;
import com.kafein.todo.project.member.ProjectMember;
import com.kafein.todo.project.member.ProjectMemberRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Servisler arası uçlar (task-service, dashboard-service; docs/04 "ProjectClient"). Gateway {@code /internal/**}
 * yolunu route etmez; dışarıdan erişilemez. Çağıran servis kullanıcının token'ını iletir, bu yüzden geçerli bir
 * token yeterlidir (auth-service'in dahili uçlarıyla aynı kural).
 */
@RestController
@RequestMapping("/internal/projects")
public class InternalProjectController {

    private final ProjectMemberRepository members;

    public InternalProjectController(ProjectMemberRepository members) {
        this.members = members;
    }

    /**
     * Üyelik kontrolü (görev oluşturma/atama). Üye değilse veya proje yoksa 404 {@code MEMBERSHIP_NOT_FOUND}.
     * Proje durumu da döner: arşivlenmiş projede görev değişikliğine izin verip vermemek task-service'in kararı.
     */
    @GetMapping("/{projectId}/members/{userId}")
    @Transactional(readOnly = true)
    public MembershipResponse membership(@PathVariable long projectId, @PathVariable long userId) {
        ProjectMember member = members.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> ApiException.notFound("MEMBERSHIP_NOT_FOUND", "Kullanıcı bu projenin üyesi değil"));
        return MembershipResponse.from(member);
    }

    /** Kullanıcının üyesi olduğu proje id'leri (dashboard, "benim projelerimdeki görevler"). */
    @GetMapping("/accessible")
    public List<Long> accessible(@RequestParam long userId) {
        return members.findProjectIdsByUserId(userId);
    }
}
