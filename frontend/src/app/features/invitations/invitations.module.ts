import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { SharedModule } from '../../shared/shared.module';
import { MyInvitationsComponent } from './my-invitations/my-invitations.component';

/** /invitations (lazy): "Davetlerim" — bana gelen bekleyen davetleri kabul / red. */
@NgModule({
  declarations: [MyInvitationsComponent],
  imports: [SharedModule, RouterModule.forChild([{ path: '', component: MyInvitationsComponent }])],
})
export class InvitationsModule {}
