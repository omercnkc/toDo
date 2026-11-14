import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { AuthGuard } from './core/auth/auth.guard';
import { RoleGuard } from './core/auth/role.guard';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';
import { NotFoundComponent } from './layout/not-found/not-found.component';

/**
 * Her özellik ayrı lazy modül (docs/08).
 *
 * - Giriş gerektiren sayfalar `MainLayoutComponent`'in (navbar) çocuklarıdır; layout'taki
 *   `canActivate`/`canActivateChild` her gezinmeyi korur. Lazy modüller guard'lardan ÖNCE indirildiği
 *   için her birinde ayrıca `canLoad` vardır (giriş/yetki yoksa kod hiç inmez).
 * - Çocuklarda `''` yollu lazy modül yok: olsaydı `/login` eşleşmesi denenirken o modülün `canLoad`'u
 *   çalışır ve girişsiz kullanıcıyı `/login?returnUrl=/login`'e atardı.
 * - Sıra önemli: layout ve auth modülü `''` yolunda; eşleşmeyen her şey en sondaki 404'e düşer.
 */
const routes: Routes = [
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [AuthGuard],
    canActivateChild: [AuthGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        canLoad: [AuthGuard],
        loadChildren: () => import('./features/dashboard/dashboard.module').then((m) => m.DashboardModule),
      },
      {
        path: 'profile',
        canLoad: [AuthGuard],
        loadChildren: () => import('./features/profile/profile.module').then((m) => m.ProfileModule),
      },
      {
        path: 'admin/users',
        canLoad: [RoleGuard],
        canActivate: [RoleGuard],
        data: { roles: ['ADMIN'] },
        loadChildren: () => import('./features/users/users.module').then((m) => m.UsersModule),
      },
    ],
  },
  {
    path: '',
    loadChildren: () => import('./features/auth/auth.module').then((m) => m.AuthModule),
  },
  { path: '**', component: NotFoundComponent },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
