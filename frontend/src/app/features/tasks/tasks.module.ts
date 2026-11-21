import { A11yModule } from '@angular/cdk/a11y';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule, Routes } from '@angular/router';

import { SharedModule } from '../../shared/shared.module';
import { KanbanBoardComponent } from './kanban/kanban-board.component';
import { TaskListComponent } from './task-list/task-list.component';
import { TaskModalComponent } from './task-modal/task-modal.component';
import { TaskViewSwitchComponent } from './view-switch/task-view-switch.component';

/** `/projects/:id/...` altındaki görev sayfaları; `:id` üst route'ta (projects modülü). */
const routes: Routes = [
  { path: 'board', component: KanbanBoardComponent, data: { title: 'Kanban panosu' } },
  { path: 'tasks', component: TaskListComponent, data: { title: 'Görev listesi' } },
];

/**
 * Görevler (lazy, projects modülünün içinden yüklenir): Kanban panosu, görev listesi ve ortak görev modalı. CDK yalnızca bu pakette;
 * proje sayfalarını ağırlaştırmaz. `cdkScrollable`: dar ekranda yatay kaydırılan pano sürüklerken otomatik kaysın;
 * `A11yModule`: modal açıkken odak modalda kalır.
 */
@NgModule({
  declarations: [KanbanBoardComponent, TaskListComponent, TaskModalComponent, TaskViewSwitchComponent],
  imports: [SharedModule, ReactiveFormsModule, DragDropModule, ScrollingModule, A11yModule, RouterModule.forChild(routes)],
})
export class TasksModule {}
