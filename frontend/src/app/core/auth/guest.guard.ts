import { Injectable } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';

import { AuthService } from './auth.service';

/** Login/register: giriş yapmış kullanıcı bu sayfaları görmez, dashboard'a gider. */
@Injectable({ providedIn: 'root' })
export class GuestGuard implements CanActivate {
  constructor(private readonly auth: AuthService, private readonly router: Router) {}

  canActivate(): boolean | UrlTree {
    return this.auth.isAuthenticated ? this.router.createUrlTree(['/dashboard']) : true;
  }
}
