import { ChangeDetectionStrategy, Component } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';

/**
 * Giriş sonrası açılış sayfası. Şimdilik karşılama; özet sayılar ve grafikler Faz 6'da gelir.
 * Çıkış ve profil bağlantıları navbar'da (layout).
 */
@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent {
  readonly user$ = this.auth.currentUser$;

  constructor(private readonly auth: AuthService) {}
}
