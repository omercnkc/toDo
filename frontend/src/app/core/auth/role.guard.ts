import { Injectable } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivate,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';

import { Role } from '../models/user.model';
import { ToastService } from '../services/toast.service';
import { AuthService } from './auth.service';

/**
 * Rol gerektiren sayfalar. Route'ta `data: { roles: ['ADMIN'] }` verilir; AuthGuard'dan sonra kullanılır.
 * Yetki yoksa bildirim gösterip dashboard'a döner. (Asıl yetki kontrolü backend'de; bu yalnızca arayüz.)
 */
@Injectable({ providedIn: 'root' })
export class RoleGuard implements CanActivate {
  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
    private readonly toast: ToastService,
  ) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | UrlTree {
    if (!this.auth.isAuthenticated) {
      return this.router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
    }
    const roles = (route.data['roles'] as Role[] | undefined) ?? [];
    if (roles.length === 0 || roles.some((role) => this.auth.hasRole(role))) {
      return true;
    }
    this.toast.error('Bu sayfayı görüntüleme yetkiniz yok.');
    return this.router.createUrlTree(['/dashboard']);
  }
}
