import { ChangeDetectionStrategy, Component } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';

/**
 * Geçici giriş sonrası sayfası (2-F): korumalı bir sayfa ve çıkış düğmesi sağlar.
 * Navbar/layout 2-G'de, asıl dashboard içeriği Faz 6'da gelir.
 */
@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent {
  readonly user$ = this.auth.currentUser$;
  loggingOut = false;

  constructor(private readonly auth: AuthService) {}

  logout(): void {
    this.loggingOut = true;
    this.auth.logout().subscribe();
  }
}
