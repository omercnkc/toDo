import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { fromEvent, interval, merge, Subject } from 'rxjs';
import { filter, share, startWith, switchMap, takeUntil } from 'rxjs/operators';

import { AuthService } from '../../core/auth/auth.service';
import { InvitationService } from '../../core/services/invitation.service';

/** Bekleyen davet rozetinin periyodik tazelenme aralığı (bildirim altyapısı yok; docs/01). */
export const INVITATION_POLL_MS = 60_000;

/**
 * Üst menü. Mobilde hamburger düğmesiyle açılır; sayfa değişince kendiliğinden kapanır.
 * Kullanıcı menüsü (profil, çıkış) Bootstrap dropdown'ı (data-bs-toggle; bootstrap.bundle global yüklü).
 * ADMIN bağlantıları yalnızca ADMIN'e görünür — asıl yetki kontrolü RoleGuard ve backend'de.
 *
 * "Davetlerim" rozeti: açılışta, her sayfa değişiminde, sekmeye dönülünce ve 60 sn'de bir tazelenir;
 * sekme arka plandayken istek atılmaz.
 */
@Component({
  selector: 'app-navbar',
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavbarComponent implements OnDestroy {
  readonly user$ = this.auth.currentUser$;
  readonly pendingInvitations$ = this.invitations.pendingCount$;
  menuOpen = false;
  loggingOut = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly auth: AuthService,
    private readonly invitations: InvitationService,
    router: Router,
    private readonly cdr: ChangeDetectorRef,
  ) {
    const navigationEnd$ = router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      share(),
    );
    navigationEnd$.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.menuOpen = false;
      this.cdr.markForCheck();
    });

    // startWith: ilk tazeleme abone olunduğu anda (timer(0) bir sonraki turu beklerdi)
    merge(interval(INVITATION_POLL_MS).pipe(startWith(0)), navigationEnd$, fromEvent(document, 'visibilitychange'))
      .pipe(
        filter(() => document.visibilityState === 'visible'),
        switchMap(() => this.invitations.refreshPendingCount()),
        takeUntil(this.destroy$),
      )
      .subscribe();
  }

  toggleMenu(): void {
    this.menuOpen = !this.menuOpen;
  }

  logout(): void {
    if (this.loggingOut) {
      return;
    }
    this.loggingOut = true;
    this.auth.logout().subscribe();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    // Çıkışta layout kapanır: bir sonraki kullanıcı eski sayıyı görmesin
    this.invitations.reset();
  }
}
