import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable, of, Subject } from 'rxjs';
import { catchError, map, switchMap, takeUntil } from 'rxjs/operators';

import { AuthService } from '../../../core/auth/auth.service';
import { extractApiError } from '../../../core/models/api-error.model';
import { displayName, Invitation, Project, ProjectMember } from '../../../core/models/project.model';
import { UserSummary } from '../../../core/models/user.model';
import { ProjectService } from '../../../core/services/project.service';
import { ToastService } from '../../../core/services/toast.service';

export type DetailError = 'notFound' | 'forbidden' | 'failed';

/**
 * Proje detayı (`/projects/:id`): bilgiler, üyeler; yönetebilene (OWNER/ADMIN) düzenle/arşivle,
 * üye çıkarma, davet gönderme ve bekleyen davetleri iptal. Arşivlenmiş projede üyelik değişmez.
 *
 * Erişimi olmayan veya olmayan proje sayfada açıklanır (genel bildirim gösterilmez).
 */
@Component({
  selector: 'app-project-detail',
  templateUrl: './project-detail.component.html',
  styleUrls: ['./project-detail.component.scss'],
})
export class ProjectDetailComponent implements OnInit, OnDestroy {
  readonly displayName = displayName;
  readonly currentUserId = this.auth.currentUser?.id ?? null;

  projectId = 0;
  project: Project | null = null;
  members: ProjectMember[] = [];
  pendingInvitations: Invitation[] = [];
  /** Davet panelinin girdileri (OnPush: yeni dizi verilir). */
  memberIds: number[] = [];
  pendingInviteeIds: number[] = [];

  loading = true;
  error: DetailError | null = null;
  membersFailed = false;
  /** Süren işlemler (ilgili düğme kilitlenir). */
  busy = { archive: false, removingUserId: null as number | null, invitingUserId: null as number | null, cancellingId: null as number | null };

  private readonly reload$ = new Subject<void>();
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly projects: ProjectService,
    private readonly auth: AuthService,
    private readonly toast: ToastService,
    private readonly route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    // Aynı bileşen içinde başka projeye geçilirse (paramMap değişir) yeniden yüklenir
    this.route.paramMap
      .pipe(
        map((params) => Number(params.get('id'))),
        switchMap((id) => {
          this.projectId = id;
          if (!Number.isInteger(id) || id <= 0) {
            this.loading = false;
            this.project = null;
            this.error = 'notFound';
            return of(null);
          }
          return this.loadAll();
        }),
        takeUntil(this.destroy$),
      )
      .subscribe();
    this.reload$
      .pipe(
        switchMap(() => this.loadAll()),
        takeUntil(this.destroy$),
      )
      .subscribe();
  }

  get canChangeMembership(): boolean {
    return !!this.project && this.project.canManage && this.project.status === 'ACTIVE';
  }

  roleLabel(project: Project): string {
    if (project.myRole === 'OWNER') {
      return 'Sahibi';
    }
    return project.myRole === 'MEMBER' ? 'Üye' : 'Yönetici erişimi';
  }

  // ---------- proje işlemleri ----------

  archive(): void {
    const project = this.project;
    if (!project || !confirm(`"${project.name}" arşivlensin mi? Arşivden istediğin zaman geri alabilirsin.`)) {
      return;
    }
    this.busy.archive = true;
    this.run(this.projects.archive(project.id), `"${project.name}" arşivlendi.`, () => (this.busy.archive = false));
  }

  restore(): void {
    const project = this.project;
    if (!project) {
      return;
    }
    this.busy.archive = true;
    this.run(this.projects.restore(project), `"${project.name}" arşivden çıkarıldı.`, () => (this.busy.archive = false));
  }

  // ---------- üyeler ----------

  removeMember(member: ProjectMember): void {
    const name = displayName(member, member.userId);
    if (!confirm(`${name} projeden çıkarılsın mı? Yeniden eklemek için tekrar davet etmen gerekir.`)) {
      return;
    }
    this.busy.removingUserId = member.userId;
    this.run(this.projects.removeMember(this.projectId, member.userId), `${name} projeden çıkarıldı.`,
      () => (this.busy.removingUserId = null));
  }

  // ---------- davetler ----------

  invite(user: UserSummary): void {
    this.busy.invitingUserId = user.id;
    this.run(this.projects.invite(this.projectId, user.id), `${user.fullName} davet edildi.`,
      () => (this.busy.invitingUserId = null));
  }

  cancelInvitation(invitation: Invitation): void {
    const name = displayName(invitation.invitee, invitation.invitee.id);
    if (!confirm(`${name} için gönderilen davet geri çekilsin mi?`)) {
      return;
    }
    this.busy.cancellingId = invitation.id;
    this.run(this.projects.cancelInvitation(this.projectId, invitation.id), 'Davet geri çekildi.',
      () => (this.busy.cancellingId = null));
  }

  retry(): void {
    this.reload$.next();
  }

  trackMember(_index: number, member: ProjectMember): number {
    return member.userId;
  }

  trackInvitation(_index: number, invitation: Invitation): number {
    return invitation.id;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * İşlem sonrası her durumda sayfa yeniden yüklenir: başarıda güncel hâl, hatada (örn. arada başkası
   * değiştirdi → 409) yine güncel hâl görünsün.
   */
  private run(action: Observable<unknown>, doneText: string, finish: () => void): void {
    action.subscribe({
      next: () => {
        finish();
        this.toast.success(doneText);
        this.reload$.next();
      },
      error: (error: unknown) => {
        finish();
        // 0/5xx/403'ü ErrorInterceptor gösterir
        if (error instanceof HttpErrorResponse && error.status > 0 && error.status < 500 && error.status !== 403) {
          this.toast.error(extractApiError(error)?.message ?? 'İşlem tamamlanamadı.');
        }
        this.reload$.next();
      },
    });
  }

  /** Önce proje (erişim kontrolü), sonra üyeler ve — yönetebilene — bekleyen davetler. */
  private loadAll(): Observable<unknown> {
    if (!this.project) {
      this.loading = true;
    }
    this.error = null;
    return this.projects.get(this.projectId, true).pipe(
      switchMap((project) => {
        this.project = project;
        return this.loadMembersAndInvitations(project);
      }),
      catchError((error: unknown) => {
        this.loading = false;
        this.project = null;
        this.error = toDetailError(error);
        return of(null);
      }),
    );
  }

  private loadMembersAndInvitations(project: Project): Observable<unknown> {
    const invitations$ = project.canManage && project.status === 'ACTIVE'
      ? this.projects.invitations(project.id, 'PENDING').pipe(catchError(() => of([] as Invitation[])))
      : of([] as Invitation[]);
    return this.projects.members(project.id).pipe(
      catchError(() => {
        this.membersFailed = true;
        return of(null);
      }),
      switchMap((members) => {
        if (members) {
          this.membersFailed = false;
          this.members = members;
          this.memberIds = members.map((m) => m.userId);
        }
        return invitations$;
      }),
      map((invitations) => {
        this.pendingInvitations = invitations;
        this.pendingInviteeIds = invitations.map((i) => i.invitee.id);
        this.loading = false;
        return null;
      }),
    );
  }
}

function toDetailError(error: unknown): DetailError {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 404 || error.status === 400) {
      return 'notFound';
    }
    if (error.status === 403) {
      return 'forbidden';
    }
  }
  return 'failed';
}
