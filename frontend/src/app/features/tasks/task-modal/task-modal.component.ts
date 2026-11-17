import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  EventEmitter,
  HostListener,
  Inject,
  Input,
  OnDestroy,
  OnInit,
  Output,
  Renderer2,
} from '@angular/core';
import { AbstractControl, FormBuilder, Validators } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { finalize, switchMap } from 'rxjs/operators';

import { extractApiError } from '../../../core/models/api-error.model';
import { displayName, ProjectMember } from '../../../core/models/project.model';
import {
  Task,
  TASK_DESCRIPTION_MAX,
  TASK_PRIORITIES,
  TASK_PRIORITY_LABELS,
  TASK_STATUS_LABELS,
  TASK_STATUSES,
  TASK_TITLE_MAX,
  TaskPriority,
  TaskStatus,
} from '../../../core/models/task.model';
import { TaskService } from '../../../core/services/task.service';
import { ToastService } from '../../../core/services/toast.service';
import { applyServerErrors } from '../../../shared/forms/server-errors';
import { notBlank } from '../../../shared/forms/validators';

type TaskField = 'title' | 'description' | 'priority' | 'assigneeId' | 'dueDate' | 'status';

/**
 * Görev oluşturma / görüntüleme-düzenleme / silme modalı (docs/08 "görev detay/form modal").
 * Bootstrap modal görünümü Angular tarafından çizilir (Bootstrap JS kullanılmaz): odak modalda tutulur ve
 * kapanınca açan öğeye döner (`cdkTrapFocus`), Esc ve arka plan tıklaması kapatır, sayfa kaydırması kilitlenir.
 *
 * Düzenlemede durum da seçilebilir (klavye kullanıcıları için sürükle-bırakın karşılığı): önce içerik
 * kaydedilir (PUT), durum değiştiyse görev yeni kolonun sonuna taşınır.
 */
@Component({
  selector: 'app-task-modal',
  templateUrl: './task-modal.component.html',
})
export class TaskModalComponent implements OnInit, OnDestroy {
  @Input() projectId = 0;
  /** null → yeni görev. */
  @Input() task: Task | null = null;
  @Input() members: ProjectMember[] = [];
  /** Arşivlenmiş proje: yalnızca görüntüleme. */
  @Input() readOnly = false;
  /** Görev oluşturuldu/değişti/silindi: pano yenilensin. */
  @Output() changed = new EventEmitter<void>();
  @Output() closed = new EventEmitter<void>();

  readonly titleMax = TASK_TITLE_MAX;
  readonly descriptionMax = TASK_DESCRIPTION_MAX;
  readonly priorities = TASK_PRIORITIES;
  readonly priorityLabels = TASK_PRIORITY_LABELS;
  readonly statuses = TASK_STATUSES;
  readonly statusLabels = TASK_STATUS_LABELS;
  readonly displayName = displayName;

  readonly form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(TASK_TITLE_MAX), notBlank]],
    description: ['', Validators.maxLength(TASK_DESCRIPTION_MAX)],
    priority: ['MEDIUM' as TaskPriority, Validators.required],
    /** select değeri metin: '' = atanmamış */
    assigneeId: [''],
    dueDate: [''],
    status: ['TODO' as TaskStatus],
  });

  submitting = false;
  deleting = false;
  formError: string | null = null;

  constructor(
    private readonly fb: FormBuilder,
    private readonly tasks: TaskService,
    private readonly toast: ToastService,
    private readonly renderer: Renderer2,
    @Inject(DOCUMENT) private readonly document: Document,
  ) {}

  ngOnInit(): void {
    this.renderer.addClass(this.document.body, 'modal-open');
    if (this.task) {
      this.form.setValue({
        title: this.task.title,
        description: this.task.description ?? '',
        priority: this.task.priority,
        assigneeId: this.task.assigneeId === null ? '' : String(this.task.assigneeId),
        dueDate: this.task.dueDate ?? '',
        status: this.task.status,
      });
    }
    if (this.readOnly) {
      this.form.disable();
    }
  }

  get isEdit(): boolean {
    return this.task !== null;
  }

  get busy(): boolean {
    return this.submitting || this.deleting;
  }

  /** Atanan kişi artık üye değilse (çıkarılmış) seçenek listesinde yine görünsün. */
  get assigneeMissing(): boolean {
    const id = this.task?.assigneeId;
    return id != null && !this.members.some((m) => m.userId === id);
  }

  memberName(member: ProjectMember): string {
    return displayName(member, member.userId);
  }

  reporterName(): string {
    const id = this.task?.reporterId;
    const member = this.members.find((m) => m.userId === id);
    return member ? displayName(member, member.userId) : `Kullanıcı #${id}`;
  }

  control(name: TaskField): AbstractControl {
    return this.form.get(name) as AbstractControl;
  }

  isInvalid(name: TaskField): boolean {
    const control = this.control(name);
    return control.invalid && (control.touched || control.dirty);
  }

  get descriptionLength(): number {
    return ((this.control('description').value as string | null) ?? '').length;
  }

  @HostListener('document:keydown.escape')
  close(): void {
    if (!this.busy) {
      this.closed.emit();
    }
  }

  /** Yalnızca karartılmış alana tıklayınca (diyalogun içine değil). */
  onBackdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.close();
    }
  }

  submit(): void {
    this.formError = null;
    if (this.readOnly || this.busy) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue() as Record<TaskField, string>;
    const fields = {
      title: value.title.trim(),
      description: value.description.trim() || null,
      priority: value.priority as TaskPriority,
      assigneeId: value.assigneeId ? Number(value.assigneeId) : null,
      dueDate: value.dueDate || null,
    };
    const task = this.task;
    let request: Observable<Task>;
    if (task) {
      const targetStatus = value.status as TaskStatus;
      request = this.tasks.update(task.id, fields).pipe(
        switchMap((updated) =>
          updated.status !== targetStatus ? this.tasks.changeStatus(task.id, targetStatus) : of(updated)),
      );
    } else {
      request = this.tasks.create({ projectId: this.projectId, ...fields });
    }

    this.submitting = true;
    request.pipe(finalize(() => (this.submitting = false))).subscribe({
      next: () => {
        this.toast.success(task ? 'Görev kaydedildi.' : 'Görev oluşturuldu.');
        this.changed.emit();
        this.closed.emit();
      },
      error: (error: unknown) => this.handleError(error),
    });
  }

  remove(): void {
    const task = this.task;
    if (!task || this.busy || !confirm(`"${task.title}" silinsin mi? Bu işlem geri alınamaz.`)) {
      return;
    }
    this.formError = null;
    this.deleting = true;
    this.tasks
      .delete(task.id)
      .pipe(finalize(() => (this.deleting = false)))
      .subscribe({
        next: () => {
          this.toast.success('Görev silindi.');
          this.changed.emit();
          this.closed.emit();
        },
        error: (error: unknown) => this.handleError(error),
      });
  }

  ngOnDestroy(): void {
    this.renderer.removeClass(this.document.body, 'modal-open');
  }

  private handleError(error: unknown): void {
    // Ağ hatası, 5xx ve 403'ü ErrorInterceptor bildirir
    if (error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500 || error.status === 403)) {
      return;
    }
    const apiError = extractApiError(error);
    this.formError = apiError ? applyServerErrors(this.form, apiError) : 'İşlem tamamlanamadı. Lütfen tekrar deneyin.';
    // Görev silinmiş / proje arşivlenmiş olabilir: pano güncel hâli göstersin
    if (error instanceof HttpErrorResponse && (error.status === 404 || error.status === 409)) {
      this.changed.emit();
    }
  }
}
