import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { AuthGuard } from './core/auth/auth.guard';

/**
 * Her özellik ayrı lazy modül (docs/08). Korumalı modüllerde `canLoad` giriş yoksa kodu hiç
 * indirmez, `canActivate` ise modül yüklendikten sonraki gezinmeleri korur.
 * Sıra önemli: auth modülü '' yolunda olduğu için diğer yollardan sonra gelir.
 */
const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    canLoad: [AuthGuard],
    canActivate: [AuthGuard],
    loadChildren: () => import('./features/dashboard/dashboard.module').then((m) => m.DashboardModule),
  },
  {
    path: '',
    loadChildren: () => import('./features/auth/auth.module').then((m) => m.AuthModule),
  },
  // 2-G'de "sayfa bulunamadı" ekranı gelecek
  { path: '**', redirectTo: 'dashboard' },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
