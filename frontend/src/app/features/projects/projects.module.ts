import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule, Routes } from '@angular/router';

import { SharedModule } from '../../shared/shared.module';
import { InvitePanelComponent } from './invite-panel/invite-panel.component';
import { ProjectDetailComponent } from './project-detail/project-detail.component';
import { ProjectFormComponent } from './project-form/project-form.component';
import { ProjectListComponent } from './project-list/project-list.component';

/** `new`, `:id`'den önce gelmeli. */
const routes: Routes = [
  { path: '', component: ProjectListComponent },
  { path: 'new', component: ProjectFormComponent },
  { path: ':id', component: ProjectDetailComponent },
  { path: ':id/edit', component: ProjectFormComponent },
];

/** /projects (lazy): liste, oluşturma/düzenleme, detay (üyeler ve davetler). */
@NgModule({
  declarations: [ProjectListComponent, ProjectFormComponent, ProjectDetailComponent, InvitePanelComponent],
  imports: [SharedModule, ReactiveFormsModule, RouterModule.forChild(routes)],
})
export class ProjectsModule {}
