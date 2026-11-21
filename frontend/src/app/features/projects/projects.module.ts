import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule, Routes } from '@angular/router';

import { SharedModule } from '../../shared/shared.module';
import { InvitePanelComponent } from './invite-panel/invite-panel.component';
import { ProjectDetailComponent } from './project-detail/project-detail.component';
import { ProjectFormComponent } from './project-form/project-form.component';
import { ProjectListComponent } from './project-list/project-list.component';

/**
 * `new`, `:id`'den önce gelmeli. `:id/board` gibi görev sayfaları ayrı lazy modülde (`TasksModule`);
 * çocuksuz `:id` route'u yalnızca tam `/projects/5` ile eşleştiği için `/projects/5/board` son route'a düşer.
 */
const routes: Routes = [
  { path: '', component: ProjectListComponent, data: { title: 'Projeler' } },
  { path: 'new', component: ProjectFormComponent, data: { title: 'Yeni proje' } },
  { path: ':id', component: ProjectDetailComponent, data: { title: 'Proje' } },
  { path: ':id/edit', component: ProjectFormComponent, data: { title: 'Projeyi düzenle' } },
  { path: ':id', loadChildren: () => import('../tasks/tasks.module').then((m) => m.TasksModule) },
];

/** /projects (lazy): liste, oluşturma/düzenleme, detay (üyeler ve davetler). */
@NgModule({
  declarations: [ProjectListComponent, ProjectFormComponent, ProjectDetailComponent, InvitePanelComponent],
  imports: [SharedModule, ReactiveFormsModule, RouterModule.forChild(routes)],
})
export class ProjectsModule {}
