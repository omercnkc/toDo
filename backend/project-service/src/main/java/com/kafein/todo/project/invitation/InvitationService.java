package com.kafein.todo.project.invitation;

import com.kafein.todo.common.error.ApiException;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.project.access.ProjectAccessService;
import com.kafein.todo.project.client.UserDirectory;
import com.kafein.todo.project.client.UserSummary;
import com.kafein.todo.project.invitation.dto.InvitationResponse;
import com.kafein.todo.project.member.ProjectMember;
import com.kafein.todo.project.member.ProjectMemberRepository;
import com.kafein.todo.project.project.Project;
import com.kafein.todo.project.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Davet akışı (docs/04, docs/06). Üyelik ancak davet edilen kabul edince oluşur.
 * <p>
 * auth-service çağrıları transaction DIŞINDA yapılır (uzak çağrı sürerken veritabanı bağlantısı ve kilitler
 * tutulmasın); veritabanı işleri kısa {@link TransactionTemplate} blokları içinde. Bu yüzden yerel kurallar
 * kayıttan hemen önce transaction içinde yeniden kontrol edilir (arada proje arşivlenmiş olabilir).
 */
@Service
public class InvitationService {

    /** Reddedilen kişiye bu süre boyunca aynı projeden yeniden davet gönderilemez. */
    static final Duration REJECTION_COOLDOWN = Duration.ofHours(24);
    /** Bir projede aynı anda en fazla bu kadar bekleyen davet. */
    static final int MAX_PENDING_PER_PROJECT = 50;
    /** Liste uçları en yeni bu kadar daveti döndürür (sayfalama gerekmeyecek kadar küçük tutulur). */
    private static final Pageable LIST_LIMIT = PageRequest.of(0, 200);

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

    private final ProjectInvitationRepository invitations;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectAccessService access;
    private final UserDirectory users;
    private final TransactionTemplate tx;
    private final Clock clock;

    public InvitationService(ProjectInvitationRepository invitations, ProjectRepository projects,
                             ProjectMemberRepository members, ProjectAccessService access, UserDirectory users,
                             PlatformTransactionManager transactionManager, Clock clock) {
        this.invitations = invitations;
        this.projects = projects;
        this.members = members;
        this.access = access;
        this.users = users;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // ---------- proje tarafı (OWNER / ADMIN) ----------

    /**
     * Davet gönderir. Kurallar (sırayla; ucuz yerel kontroller uzak çağrıdan önce):
     * arşivlenmiş proje 409 · kendini davet 400 · zaten üye 409 · bekleyen davet var 409 ·
     * son 24 saatte reddetti 409 · 50 bekleyen sınırı 409 · kullanıcı yok 404 · pasif kullanıcı 400.
     */
    public InvitationResponse invite(AuthenticatedUser user, long projectId, long inviteeId) {
        access.requireManager(projectId, user);
        if (inviteeId == user.id()) {
            throw ApiException.badRequest("CANNOT_INVITE_SELF", "Kendinizi davet edemezsiniz");
        }
        tx.executeWithoutResult(status -> checkInviteRules(projectId, inviteeId));

        Map<Long, UserSummary> people = users.lookup(List.of(inviteeId, user.id()));
        UserSummary invitee = people.get(inviteeId);
        if (invitee == null) {
            throw ApiException.notFound("USER_NOT_FOUND", "Kullanıcı bulunamadı");
        }
        if (!invitee.enabled()) {
            throw ApiException.badRequest("USER_DISABLED", "Pasif kullanıcı davet edilemez");
        }

        ProjectInvitation saved = tx.execute(status -> {
            Project project = checkInviteRules(projectId, inviteeId);
            try {
                return invitations.saveAndFlush(new ProjectInvitation(project, inviteeId, user.id()));
            } catch (DataIntegrityViolationException e) {
                // Aynı anda gelen iki davet: yerel kontrolü ikisi de geçti, unique (pending_flag) ikinciyi durdurdu
                throw pendingExists();
            }
        });
        log.info("Kullanıcı {} proje {}'e kullanıcı {}'i davet etti", user.id(), projectId, inviteeId);
        return InvitationResponse.from(saved, people);
    }

    /** Projenin davetleri, yeni önce (en fazla 200). {@code status} null → hepsi. */
    public List<InvitationResponse> listForProject(AuthenticatedUser user, long projectId, InvitationStatus status) {
        access.requireManager(projectId, user);
        List<ProjectInvitation> list = tx.execute(s -> invitations.findForProject(projectId, status, LIST_LIMIT));
        return withNames(list);
    }

    /** Bekleyen daveti geri çeker (CANCELLED). */
    public void cancel(AuthenticatedUser user, long projectId, long invitationId) {
        access.requireManager(projectId, user);
        tx.executeWithoutResult(status -> {
            ProjectInvitation invitation = invitations.findForUpdate(invitationId)
                    .filter(i -> i.getProject().getId() == projectId)
                    .orElseThrow(InvitationService::notFound);
            requirePending(invitation);
            invitation.cancel(now());
        });
        log.info("Kullanıcı {} proje {}'deki davet {}'i iptal etti", user.id(), projectId, invitationId);
    }

    // ---------- davet edilen tarafı ----------

    /** Bana gelen davetler (varsayılan: bekleyenler), yeni önce. */
    public List<InvitationResponse> listMine(AuthenticatedUser user, InvitationStatus status) {
        List<ProjectInvitation> list = tx.execute(s -> invitations.findForInvitee(user.id(), status, LIST_LIMIT));
        return withNames(list);
    }

    /** Kabul: davet ACCEPTED + MEMBER üyeliği tek transaction'da. */
    public InvitationResponse accept(AuthenticatedUser user, long invitationId) {
        ProjectInvitation accepted = tx.execute(status -> {
            ProjectInvitation invitation = lockOwnPendingInvitation(user, invitationId);
            Project project = invitation.getProject();
            if (project.isArchived()) {
                throw ApiException.conflict("PROJECT_ARCHIVED", "Arşivlenmiş projenin daveti kabul edilemez");
            }
            invitation.accept(now());
            if (!members.existsByProjectIdAndUserId(project.getId(), user.id())) {
                members.save(ProjectMember.member(project, user.id()));
            }
            return invitation;
        });
        log.info("Kullanıcı {} davet {}'i kabul etti, proje {}'e üye oldu",
                user.id(), invitationId, accepted.getProject().getId());
        return withNames(List.of(accepted)).get(0);
    }

    /** Red: üyelik oluşmaz; 24 saat boyunca aynı projeden yeniden davet gelmez. */
    public InvitationResponse reject(AuthenticatedUser user, long invitationId) {
        ProjectInvitation rejected = tx.execute(status -> {
            ProjectInvitation invitation = lockOwnPendingInvitation(user, invitationId);
            invitation.reject(now());
            return invitation;
        });
        log.info("Kullanıcı {} davet {}'i reddetti", user.id(), invitationId);
        return withNames(List.of(rejected)).get(0);
    }

    // ---------- yardımcılar ----------

    /** Transaction içinde çağrılır. Proje yoksa 404 (requireManager'dan sonra silinmiş olabilir). */
    private Project checkInviteRules(long projectId, long inviteeId) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("PROJECT_NOT_FOUND", "Proje bulunamadı"));
        if (project.isArchived()) {
            throw ApiException.conflict("PROJECT_ARCHIVED", "Arşivlenmiş projeye davet gönderilemez");
        }
        if (members.existsByProjectIdAndUserId(projectId, inviteeId)) {
            throw ApiException.conflict("ALREADY_MEMBER", "Bu kullanıcı zaten projenin üyesi");
        }
        if (invitations.existsByProjectIdAndInviteeIdAndStatus(projectId, inviteeId, InvitationStatus.PENDING)) {
            throw pendingExists();
        }
        Instant cooldownStart = now().minus(REJECTION_COOLDOWN);
        if (invitations.existsByProjectIdAndInviteeIdAndStatusAndRespondedAtAfter(
                projectId, inviteeId, InvitationStatus.REJECTED, cooldownStart)) {
            throw ApiException.conflict("INVITATION_RECENTLY_REJECTED",
                    "Bu kullanıcı davetinizi son 24 saat içinde reddetti; daha sonra tekrar deneyin");
        }
        if (invitations.countByProjectIdAndStatus(projectId, InvitationStatus.PENDING) >= MAX_PENDING_PER_PROJECT) {
            throw ApiException.conflict("TOO_MANY_PENDING_INVITATIONS",
                    "Projede en fazla " + MAX_PENDING_PER_PROJECT + " bekleyen davet olabilir");
        }
        return project;
    }

    /** Davet yalnızca davet edilen kişi tarafından yanıtlanır (ADMIN dahil başkası adına yapılamaz; docs/07). */
    private ProjectInvitation lockOwnPendingInvitation(AuthenticatedUser user, long invitationId) {
        ProjectInvitation invitation = invitations.findForUpdate(invitationId).orElseThrow(InvitationService::notFound);
        if (invitation.getInviteeId() != user.id()) {
            throw ApiException.forbidden("INVITATION_FORBIDDEN", "Bu davet size gönderilmedi");
        }
        requirePending(invitation);
        return invitation;
    }

    /** DATETIME(6) mikro saniye saklar; yanıt sonradan okunan değerle aynı olsun. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static void requirePending(ProjectInvitation invitation) {
        if (!invitation.isPending()) {
            throw ApiException.conflict("INVITATION_NOT_PENDING", "Bu davet zaten yanıtlanmış veya iptal edilmiş");
        }
    }

    /** Ad/e-posta tek toplu çağrıyla; auth-service'e ulaşılamazsa adsız. */
    private List<InvitationResponse> withNames(List<ProjectInvitation> list) {
        Set<Long> ids = new HashSet<>();
        list.forEach(i -> {
            ids.add(i.getInviterId());
            ids.add(i.getInviteeId());
        });
        Map<Long, UserSummary> people = users.lookupBestEffort(ids);
        return list.stream().map(i -> InvitationResponse.from(i, people)).toList();
    }

    private static ApiException pendingExists() {
        return ApiException.conflict("INVITATION_ALREADY_PENDING", "Bu kullanıcının bu projede bekleyen bir daveti var");
    }

    private static ApiException notFound() {
        return ApiException.notFound("INVITATION_NOT_FOUND", "Davet bulunamadı");
    }
}
