import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { filter, takeUntil } from 'rxjs/operators';

import { AuthService } from '../../core/auth/auth.service';

/**
 * Üst menü. Mobilde hamburger düğmesiyle açılır; sayfa değişince kendiliğinden kapanır.
 * Kullanıcı menüsü (profil, çıkış) Bootstrap dropdown'ı (data-bs-toggle; bootstrap.bundle global yüklü).
 * ADMIN bağlantıları yalnızca ADMIN'e görünür — asıl yetki kontrolü RoleGuard ve backend'de.
 */
@Component({
  selector: 'app-navbar',
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavbarComponent implements OnDestroy {
  readonly user$ = this.auth.currentUser$;
  menuOpen = false;
  loggingOut = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly auth: AuthService,
    router: Router,
    private readonly cdr: ChangeDetectorRef,
  ) {
    router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntil(this.destroy$),
      )
      .subscribe(() => {
        this.menuOpen = false;
        this.cdr.markForCheck();
      });
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
  }
}
