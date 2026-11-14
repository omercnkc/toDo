import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';

import { Role } from '../models/user.model';
import { ToastService } from '../services/toast.service';
import { AuthGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { GuestGuard } from './guest.guard';
import { RoleGuard } from './role.guard';

class FakeAuth {
  isAuthenticated = false;
  role: Role | null = null;

  hasRole(role: Role): boolean {
    return this.role === role;
  }
}

describe('Guard\'lar', () => {
  let auth: FakeAuth;
  let router: Router;
  let toast: jasmine.SpyObj<ToastService>;

  const state = (url: string) => ({ url } as RouterStateSnapshot);
  const routeWithRoles = (roles?: Role[]) => ({ data: roles ? { roles } : {} } as unknown as ActivatedRouteSnapshot);
  const serialize = (result: boolean | UrlTree) => (result instanceof UrlTree ? router.serializeUrl(result) : result);

  beforeEach(() => {
    auth = new FakeAuth();
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['error']);
    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: ToastService, useValue: toast },
      ],
    });
    router = TestBed.inject(Router);
  });

  describe('AuthGuard', () => {
    it('giriş varsa geçirir', () => {
      auth.isAuthenticated = true;
      const guard = TestBed.inject(AuthGuard);

      expect(guard.canActivate(routeWithRoles(), state('/dashboard'))).toBeTrue();
    });

    it('giriş yoksa kaldığı adresle login\'e yönlendirir', () => {
      const guard = TestBed.inject(AuthGuard);

      expect(serialize(guard.canActivate(routeWithRoles(), state('/projects/5?tab=uyeler'))))
        .toBe('/login?returnUrl=%2Fprojects%2F5%3Ftab%3Duyeler');
    });

    it('canLoad giriş yoksa modülü yükletmez', () => {
      const guard = TestBed.inject(AuthGuard);
      const segments = router.parseUrl('/admin/users').root.children['primary'].segments;

      expect(serialize(guard.canLoad({}, segments))).toBe('/login?returnUrl=%2Fadmin%2Fusers');
    });
  });

  describe('RoleGuard', () => {
    it('rolü uygunsa geçirir', () => {
      auth.isAuthenticated = true;
      auth.role = 'ADMIN';

      expect(TestBed.inject(RoleGuard).canActivate(routeWithRoles(['ADMIN']), state('/admin/users'))).toBeTrue();
    });

    it('rolü yetmezse bildirim gösterip dashboard\'a döner', () => {
      auth.isAuthenticated = true;
      auth.role = 'USER';

      const result = TestBed.inject(RoleGuard).canActivate(routeWithRoles(['ADMIN']), state('/admin/users'));

      expect(serialize(result)).toBe('/dashboard');
      expect(toast.error).toHaveBeenCalled();
    });

    it('giriş yoksa login\'e gönderir', () => {
      const result = TestBed.inject(RoleGuard).canActivate(routeWithRoles(['ADMIN']), state('/admin/users'));

      expect(serialize(result)).toBe('/login?returnUrl=%2Fadmin%2Fusers');
    });

    it('rol tanımlanmamışsa yalnızca girişe bakar', () => {
      auth.isAuthenticated = true;

      expect(TestBed.inject(RoleGuard).canActivate(routeWithRoles(), state('/x'))).toBeTrue();
    });
  });

  describe('GuestGuard', () => {
    it('anonim kullanıcıyı geçirir', () => {
      expect(TestBed.inject(GuestGuard).canActivate()).toBeTrue();
    });

    it('giriş yapmış kullanıcıyı dashboard\'a gönderir', () => {
      auth.isAuthenticated = true;

      expect(serialize(TestBed.inject(GuestGuard).canActivate())).toBe('/dashboard');
    });
  });
});
