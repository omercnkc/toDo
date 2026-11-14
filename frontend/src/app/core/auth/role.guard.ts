import { Injectable } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivate,
  CanLoad,
  Route,
  Router,
  RouterStateSnapshot,
  UrlSegment,
  UrlTree,
} from '@angular/router';

import { Role } from '../models/user.model';
import { ToastService } from '../services/toast.service';
import { AuthService } from './auth.service';

/**
 * Rol gerektiren sayfalar. Route'ta `data: { roles: ['ADMIN'] }` verilir.
 * Giriş yoksa login'e (returnUrl ile), yetki yoksa bildirim gösterip dashboard'a yönlendirir.
 * `canLoad`: yetkisi olmayanın tarayıcısına o lazy modülün kodu hiç inmez.
 * (Asıl yetki kontrolü backend'de; bu yalnızca arayüz.)
 */
@Injectable({ providedIn: 'root' })
export class RoleGuard implements CanActivate, CanLoad {
  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
    private readonly toast: ToastService,
  ) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | UrlTree {
    return this.check(route.data['roles'], state.url);
  }

  canLoad(route: Route, segments: UrlSegment[]): boolean | UrlTree {
    return this.check(route.data?.['roles'], '/' + segments.map((s) => s.path).join('/'));
  }

  private check(roles: Role[] | undefined, url: string): boolean | UrlTree {
    if (!this.auth.isAuthenticated) {
      return this.router.createUrlTree(['/login'], { queryParams: { returnUrl: url } });
    }
    if (!roles || roles.length === 0 || roles.some((role) => this.auth.hasRole(role))) {
      return true;
    }
    this.toast.error('Bu sayfayı görüntüleme yetkiniz yok.');
    return this.router.createUrlTree(['/dashboard']);
  }
}
