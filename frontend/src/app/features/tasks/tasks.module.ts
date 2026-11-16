import { DragDropModule } from '@angular/cdk/drag-drop';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { SharedModule } from '../../shared/shared.module';
import { KanbanBoardComponent } from './kanban/kanban-board.component';

/** `/projects/:id/...` altındaki görev sayfaları; `:id` üst route'ta (projects modülü). */
const routes: Routes = [{ path: 'board', component: KanbanBoardComponent }];

/**
 * Görevler (lazy, projects modülünün içinden yüklenir): Kanban panosu. CDK drag-drop yalnızca bu pakette;
 * proje sayfalarını ağırlaştırmaz. `cdkScrollable`: dar ekranda yatay kaydırılan pano sürüklerken otomatik kaysın.
 */
@NgModule({
  declarations: [KanbanBoardComponent],
  imports: [SharedModule, DragDropModule, ScrollingModule, RouterModule.forChild(routes)],
})
export class TasksModule {}
