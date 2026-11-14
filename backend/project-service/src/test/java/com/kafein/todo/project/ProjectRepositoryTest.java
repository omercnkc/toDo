package com.kafein.todo.project;

import com.kafein.todo.project.invitation.InvitationStatus;
import com.kafein.todo.project.invitation.ProjectInvitation;
import com.kafein.todo.project.invitation.ProjectInvitationRepository;
import com.kafein.todo.project.member.MemberRole;
import com.kafein.todo.project.member.ProjectMember;
import com.kafein.todo.project.member.ProjectMemberRepository;
import com.kafein.todo.project.project.Project;
import com.kafein.todo.project.project.ProjectRepository;
import com.kafein.todo.project.project.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flyway şeması + entity eşlemesi gerçek MySQL üzerinde (CHECK, UNIQUE, üretilmiş kolon, CASCADE
 * gibi kurallar ancak gerçek veritabanında doğrulanabilir). Her test kendi transaction'ında çalışır
 * ve sonunda geri alınır.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ProjectRepositoryTest {

    private static final long OWNER = 10L;
    private static final long ALICE = 20L;
    private static final long BOB = 30L;

    @Autowired
    private ProjectRepository projects;
    @Autowired
    private ProjectMemberRepository members;
    @Autowired
    private ProjectInvitationRepository invitations;
    @Autowired
    private TestEntityManager em;
    @Autowired
    private JdbcTemplate jdbc;

    private Project newProject() {
        return projects.saveAndFlush(new Project("  Web Sitesi ", "   ", OWNER));
    }

    @Test
    void projeKaydedilirDurumKodOlarakSaklanir() {
        Project saved = newProject();
        em.clear();

        Project found = projects.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("Web Sitesi");
        assertThat(found.getDescription()).as("boş açıklama NULL saklanır").isNull();
        assertThat(found.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(found.getOwnerId()).isEqualTo(OWNER);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(statusCode("projects", saved.getId())).isZero();

        found.changeStatus(ProjectStatus.ARCHIVED);
        projects.saveAndFlush(found);
        assertThat(statusCode("projects", saved.getId())).isEqualTo(1);
        assertThat(found.isArchived()).isTrue();
    }

    @Test
    void uzunAciklamaTextKolonundaSaklanir() {
        String longText = "ş".repeat(5_000);
        Project saved = projects.saveAndFlush(new Project("Büyük", longText, OWNER));
        em.clear();

        assertThat(projects.findById(saved.getId()).orElseThrow().getDescription()).isEqualTo(longText);
    }

    @Test
    void gecersizDurumKoduVeritabanindaReddedilir() {
        Project saved = newProject();

        assertThatThrownBy(() -> jdbc.update("UPDATE projects SET status = 2 WHERE id = ?", saved.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void uyelerOwnerOnceGelecekSekildeListelenirVeRolKodOlarakSaklanir() {
        Project project = newProject();
        members.saveAndFlush(ProjectMember.member(project, ALICE));
        members.saveAndFlush(ProjectMember.owner(project, OWNER));
        em.clear();

        List<ProjectMember> list = members.findByProjectIdOrderByRoleDescJoinedAtAsc(project.getId());
        assertThat(list).extracting(ProjectMember::getUserId).containsExactly(OWNER, ALICE);
        assertThat(list.get(0).getRole()).isEqualTo(MemberRole.OWNER);
        assertThat(list.get(0).isOwner()).isTrue();
        assertThat(jdbc.queryForObject("SELECT member_role FROM project_members WHERE user_id = ? AND project_id = ?",
                Integer.class, OWNER, project.getId())).isEqualTo(1);

        assertThat(members.existsByProjectIdAndUserId(project.getId(), ALICE)).isTrue();
        assertThat(members.existsByProjectIdAndUserId(project.getId(), BOB)).isFalse();
        assertThat(members.findByProjectIdAndUserId(project.getId(), ALICE)).isPresent();
    }

    @Test
    void ayniKullaniciAyniProjedeIkiKezUyeOlamaz() {
        Project project = newProject();
        members.saveAndFlush(ProjectMember.member(project, ALICE));

        assertThatThrownBy(() -> members.saveAndFlush(ProjectMember.member(project, ALICE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ayniKisiyeAyniProjedeTekBekleyenDavetOlabilir() {
        Project project = newProject();
        invitations.saveAndFlush(new ProjectInvitation(project, ALICE, OWNER));

        assertThatThrownBy(() -> invitations.saveAndFlush(new ProjectInvitation(project, ALICE, OWNER)))
                .as("ikinci bekleyen davet unique (project_id, invitee_id, pending_flag) ile reddedilir")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void yanitlanmisDavetYeniDaveteEngelOlmaz() {
        Project project = newProject();
        ProjectInvitation first = invitations.saveAndFlush(new ProjectInvitation(project, ALICE, OWNER));
        first.reject(Instant.parse("2026-11-14T10:00:00Z"));
        invitations.saveAndFlush(first);
        ProjectInvitation second = invitations.saveAndFlush(new ProjectInvitation(project, ALICE, OWNER));
        second.cancel(Instant.parse("2026-11-14T11:00:00Z"));
        invitations.saveAndFlush(second);

        ProjectInvitation third = invitations.saveAndFlush(new ProjectInvitation(project, ALICE, OWNER));

        assertThat(pendingFlag(first.getId())).isNull();
        assertThat(pendingFlag(second.getId())).isNull();
        assertThat(pendingFlag(third.getId())).isEqualTo(1);
        // Başka projede aynı kişiye bekleyen davet serbest
        invitations.saveAndFlush(new ProjectInvitation(newProject(), ALICE, OWNER));
    }

    @Test
    void davetBirKezYanitlanir() {
        Project project = newProject();
        ProjectInvitation invitation = invitations.saveAndFlush(new ProjectInvitation(project, ALICE, OWNER));
        Instant now = Instant.parse("2026-11-14T10:00:00Z");

        invitation.accept(now);
        invitations.saveAndFlush(invitation);
        em.clear();

        ProjectInvitation found = invitations.findById(invitation.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(found.getRespondedAt()).isEqualTo(now);
        assertThat(statusCode("project_invitations", invitation.getId())).isEqualTo(1);
        assertThatThrownBy(() -> found.reject(now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bekleyenDavetSorgulari() {
        Project project = newProject();
        invitations.saveAndFlush(new ProjectInvitation(project, ALICE, OWNER));
        invitations.saveAndFlush(new ProjectInvitation(project, BOB, OWNER));
        ProjectInvitation other = invitations.saveAndFlush(new ProjectInvitation(newProject(), ALICE, OWNER));
        other.reject(Instant.now());
        invitations.saveAndFlush(other);

        assertThat(invitations.findByInviteeIdAndStatusOrderByCreatedAtDesc(ALICE, InvitationStatus.PENDING))
                .hasSize(1)
                .allSatisfy(i -> assertThat(i.getProject().getId()).isEqualTo(project.getId()));
        assertThat(invitations.countByProjectIdAndStatus(project.getId(), InvitationStatus.PENDING)).isEqualTo(2);
    }

    @Test
    void projeSilinceUyelerVeDavetlerDeSilinir() {
        Project project = newProject();
        members.saveAndFlush(ProjectMember.owner(project, OWNER));
        invitations.saveAndFlush(new ProjectInvitation(project, ALICE, OWNER));
        em.clear();

        jdbc.update("DELETE FROM projects WHERE id = ?", project.getId());

        assertThat(count("project_members", project.getId())).isZero();
        assertThat(count("project_invitations", project.getId())).isZero();
    }

    private Integer statusCode(String table, long id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?", Integer.class, id);
    }

    private Integer pendingFlag(long invitationId) {
        return jdbc.queryForObject("SELECT pending_flag FROM project_invitations WHERE id = ?", Integer.class, invitationId);
    }

    private Integer count(String table, long projectId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE project_id = ?", Integer.class, projectId);
    }
}
