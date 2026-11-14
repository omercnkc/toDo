import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule, Routes } from '@angular/router';

import { SharedModule } from '../../shared/shared.module';
import { ProjectFormComponent } from './project-form/project-form.component';
import { ProjectListComponent } from './project-list/project-list.component';

/** `new`, `:id`'den önce gelmeli (3-F'de detay sayfası `:id` olacak). */
const routes: Routes = [
  { path: '', component: ProjectListComponent },
  { path: 'new', component: ProjectFormComponent },
  { path: ':id/edit', component: ProjectFormComponent },
];

/** /projects (lazy): liste, oluşturma ve düzenleme. Detay, üyeler ve davetler 3-F'de. */
@NgModule({
  declarations: [ProjectListComponent, ProjectFormComponent],
  imports: [SharedModule, ReactiveFormsModule, RouterModule.forChild(routes)],
})
export class ProjectsModule {}
