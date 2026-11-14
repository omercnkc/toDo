import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { SharedModule } from '../../shared/shared.module';
import { UserListComponent } from './user-list/user-list.component';

/** /admin/users (lazy, yalnızca ADMIN; RoleGuard app-routing'de). */
@NgModule({
  declarations: [UserListComponent],
  imports: [SharedModule, ReactiveFormsModule, RouterModule.forChild([{ path: '', component: UserListComponent }])],
})
export class UsersModule {}
