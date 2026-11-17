import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type TaskView = 'board' | 'list';

/**
 * Pano ↔ Liste geçişi (aynı projenin iki görünümü). Bağlantıdır, düğme değil: yeni sekmede açılabilir,
 * geri tuşu çalışır. Etkin görünüm `aria-current="page"` ile işaretlenir.
 */
@Component({
  selector: 'app-task-view-switch',
  template: `
    <nav class="btn-group" role="group" aria-label="Görünüm">
      <a
        [routerLink]="['/projects', projectId, 'board']"
        class="btn btn-outline-secondary btn-sm"
        [class.active]="active === 'board'"
        [attr.aria-current]="active === 'board' ? 'page' : null"
        >Pano</a
      >
      <a
        [routerLink]="['/projects', projectId, 'tasks']"
        class="btn btn-outline-secondary btn-sm"
        [class.active]="active === 'list'"
        [attr.aria-current]="active === 'list' ? 'page' : null"
        >Liste</a
      >
    </nav>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TaskViewSwitchComponent {
  @Input() projectId = 0;
  @Input() active: TaskView = 'board';
}
