import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';

import { extractApiError } from '../../../core/models/api-error.model';
import { displayName, Invitation } from '../../../core/models/project.model';
import { InvitationService } from '../../../core/services/invitation.service';
import { ToastService } from '../../../core/services/toast.service';

/**
 * Davetlerim (`/invitations`, docs/08): bana gelen bekleyen davetler. Kabul edince projeye üye olunur ve
 * istenirse doğrudan projeye gidilir; red üyelik oluşturmaz. Yanıtlanan davet listeden kalkar,
 * navbar rozeti de hemen azalır (InvitationService).
 */
@Component({
  selector: 'app-my-invitations',
  templateUrl: './my-invitations.component.html',
})
export class MyInvitationsComponent implements OnInit, OnDestroy {
  readonly displayName = displayName;

  invitations: Invitation[] = [];
  loading = true;
  loadFailed = false;
  /** Yanıtlanmakta olan davet (kartın düğmeleri kilitlenir). */
  respondingId: number | null = null;
  /** Bu oturumda kabul edilen davetler: "Projeye git" bağlantısıyla gösterilmeye devam eder. */
  accepted: Invitation[] = [];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly invitationService: InvitationService,
    private readonly toast: ToastService,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.loadFailed = false;
    this.invitationService
      .mine()
      .pipe(
        finalize(() => (this.loading = false)),
        takeUntil(this.destroy$),
      )
      .subscribe({
        next: (list) => (this.invitations = list),
        error: () => (this.loadFailed = true),
      });
  }

  accept(invitation: Invitation): void {
    this.respond(invitation, this.invitationService.accept(invitation.id), () => {
      this.accepted = [invitation, ...this.accepted];
      this.toast.success(`"${invitation.project.name}" projesine katıldın.`);
    });
  }

  reject(invitation: Invitation): void {
    const message = `"${invitation.project.name}" davetini reddetmek istiyor musun? `
      + 'Bu projeden 24 saat boyunca yeni davet alamazsın.';
    if (!confirm(message)) {
      return;
    }
    this.respond(invitation, this.invitationService.reject(invitation.id), () =>
      this.toast.success(`"${invitation.project.name}" daveti reddedildi.`));
  }

  openProject(invitation: Invitation): void {
    void this.router.navigate(['/projects', invitation.project.id]);
  }

  trackById(_index: number, invitation: Invitation): number {
    return invitation.id;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private respond(invitation: Invitation, action: Observable<Invitation>, done: () => void): void {
    this.respondingId = invitation.id;
    action
      .pipe(
        finalize(() => (this.respondingId = null)),
        takeUntil(this.destroy$),
      )
      .subscribe({
        next: () => {
          this.invitations = this.invitations.filter((i) => i.id !== invitation.id);
          done();
        },
        error: (error: unknown) => {
          // 409: davet geri çekilmiş / proje arşivlenmiş olabilir. Mesajı göster, listeyi tazele.
          if (error instanceof HttpErrorResponse && error.status > 0 && error.status < 500 && error.status !== 403) {
            this.toast.error(extractApiError(error)?.message ?? 'İşlem tamamlanamadı.');
          }
          this.load();
        },
      });
  }
}
