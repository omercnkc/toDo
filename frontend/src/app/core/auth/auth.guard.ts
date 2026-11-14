import { Injectable } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivate,
  CanActivateChild,
  CanLoad,
  Route,
  Router,
  RouterStateSnapshot,
  UrlSegment,
  UrlTree,
} from '@angular/router';

import { AuthService } from './auth.service';

/**
 * Giriş gerektiren sayfalar. Giriş yoksa kaldığı adresle (`returnUrl`) login'e yönlendirir.
 * APP_INITIALIZER yönlendirmeden önce sessiz refresh'i bitirdiği için oturum durumu burada kesindir;
 * sayfa yenilendiğinde geçerli cookie varsa kullanıcı login'e atılmaz.
 *
 * `canLoad`: giriş yoksa lazy modülün kodu hiç indirilmez.
 */
@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate, CanActivateChild, CanLoad {
  constructor(private readonly auth: AuthService, private readonly router: Router) {}

  canActivate(_route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | UrlTree {
    return this.check(state.url);
  }

  canActivateChild(_route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | UrlTree {
    return this.check(state.url);
  }

  canLoad(_route: Route, segments: UrlSegment[]): boolean | UrlTree {
    return this.check('/' + segments.map((s) => s.path).join('/'));
  }

  private check(url: string): boolean | UrlTree {
    if (this.auth.isAuthenticated) {
      return true;
    }
    return this.router.createUrlTree(['/login'], { queryParams: { returnUrl: url } });
  }
}
