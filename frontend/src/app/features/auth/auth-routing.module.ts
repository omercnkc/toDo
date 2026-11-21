import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { GuestGuard } from '../../core/auth/guest.guard';
import { LoginComponent } from './login/login.component';
import { RegisterComponent } from './register/register.component';

const routes: Routes = [
  { path: 'login', component: LoginComponent, canActivate: [GuestGuard], data: { title: 'Giriş yap' } },
  { path: 'register', component: RegisterComponent, canActivate: [GuestGuard], data: { title: 'Kayıt ol' } },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AuthRoutingModule {}
