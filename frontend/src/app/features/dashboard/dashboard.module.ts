import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { SharedModule } from '../../shared/shared.module';
import { DashboardComponent } from './dashboard.component';

/** /dashboard (lazy). */
@NgModule({
  declarations: [DashboardComponent],
  imports: [SharedModule, RouterModule.forChild([{ path: '', component: DashboardComponent }])],
})
export class DashboardModule {}
