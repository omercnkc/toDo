import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgChartsModule } from 'ng2-charts';

import { SharedModule } from '../../shared/shared.module';
import { DashboardComponent } from './dashboard.component';

/** /dashboard (lazy). Chart.js yalnızca bu pakette: diğer sayfaları ağırlaştırmaz. */
@NgModule({
  declarations: [DashboardComponent],
  imports: [SharedModule, NgChartsModule, RouterModule.forChild([{ path: '', component: DashboardComponent, data: { title: 'Panel' } }])],
})
export class DashboardModule {}
