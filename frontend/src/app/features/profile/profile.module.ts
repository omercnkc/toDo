import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { SharedModule } from '../../shared/shared.module';
import { ProfileComponent } from './profile.component';

/** /profile (lazy): kişinin kendi bilgileri, ad güncelleme ve şifre değiştirme. */
@NgModule({
  declarations: [ProfileComponent],
  imports: [SharedModule, ReactiveFormsModule, RouterModule.forChild([{ path: '', component: ProfileComponent }])],
})
export class ProfileModule {}
