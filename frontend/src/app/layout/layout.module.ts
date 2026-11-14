import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { SharedModule } from '../shared/shared.module';
import { MainLayoutComponent } from './main-layout/main-layout.component';
import { NavbarComponent } from './navbar/navbar.component';
import { NotFoundComponent } from './not-found/not-found.component';

/**
 * Uygulama çerçevesi (docs/08 "layout"): giriş sonrası sayfaları saran navbar + içerik alanı ve
 * "sayfa bulunamadı" ekranı. Küçük olduğu ve her girişte gerektiği için ana pakette (lazy değil).
 */
@NgModule({
  declarations: [MainLayoutComponent, NavbarComponent, NotFoundComponent],
  imports: [SharedModule, RouterModule],
  exports: [MainLayoutComponent, NotFoundComponent],
})
export class LayoutModule {}
