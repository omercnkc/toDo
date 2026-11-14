import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';

import { extractApiError } from '../../../core/models/api-error.model';
import { Project, PROJECT_DESCRIPTION_MAX, PROJECT_NAME_MAX } from '../../../core/models/project.model';
import { ProjectService } from '../../../core/services/project.service';
import { ToastService } from '../../../core/services/toast.service';
import { applyServerErrors } from '../../../shared/forms/server-errors';

type ProjectField = 'name' | 'description';

/**
 * Proje oluşturma (`/projects/new`) ve düzenleme (`/projects/:id/edit`).
 * Düzenlemede yetkisi olmayan (`canManage=false`) veya arşivlenmiş proje formu kilitlenir;
 * asıl kontrol yine backend'de (403 / 409).
 */
@Component({
  selector: 'app-project-form',
  templateUrl: './project-form.component.html',
})
export class ProjectFormComponent implements OnInit, OnDestroy {
  readonly nameMax = PROJECT_NAME_MAX;
  readonly descriptionMax = PROJECT_DESCRIPTION_MAX;

  readonly form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(PROJECT_NAME_MAX), notBlank]],
    description: ['', Validators.maxLength(PROJECT_DESCRIPTION_MAX)],
  });

  /** Düzenlenen proje id'si; oluşturmada null. */
  readonly projectId: number | null;
  project: Project | null = null;
  loading = false;
  loadFailed = false;
  submitting = false;
  formError: string | null = null;
  /** Düzenlenemez durum (yetki yok / arşivde): form kilitli, açıklama gösterilir. */
  lockedReason: string | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly projects: ProjectService,
    private readonly toast: ToastService,
    private readonly router: Router,
    route: ActivatedRoute,
  ) {
    const id = Number(route.snapshot.paramMap.get('id'));
    this.projectId = Number.isInteger(id) && id > 0 ? id : null;
  }

  get isEdit(): boolean {
    return this.projectId !== null;
  }

  /** Vazgeç/geri: düzenlemede projeye, oluşturmada listeye. */
  get backLink(): (string | number)[] {
    return this.isEdit ? ['/projects', this.projectId as number] : ['/projects'];
  }

  ngOnInit(): void {
    if (this.isEdit) {
      this.load();
    }
  }

  load(): void {
    this.loading = true;
    this.loadFailed = false;
    this.projects
      .get(this.projectId as number)
      .pipe(
        finalize(() => (this.loading = false)),
        takeUntil(this.destroy$),
      )
      .subscribe({
        next: (project) => this.setProject(project),
        error: (error: unknown) => {
          // 403'ü ErrorInterceptor bildirir; 404'te listeye dön
          if (error instanceof HttpErrorResponse && (error.status === 404 || error.status === 403)) {
            if (error.status === 404) {
              this.toast.error('Proje bulunamadı.');
            }
            void this.router.navigate(['/projects']);
            return;
          }
          this.loadFailed = true;
        },
      });
  }

  control(name: ProjectField): AbstractControl {
    return this.form.get(name) as AbstractControl;
  }

  isInvalid(name: ProjectField): boolean {
    const control = this.control(name);
    return control.invalid && (control.touched || control.dirty);
  }

  get descriptionLength(): number {
    return ((this.control('description').value as string | null) ?? '').length;
  }

  submit(): void {
    this.formError = null;
    if (this.lockedReason) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue() as Record<ProjectField, string | null>;
    const request = { name: (raw.name ?? '').trim(), description: (raw.description ?? '').trim() || null };
    const save$: Observable<Project> = this.isEdit
      ? this.projects.update(this.projectId as number, request)
      : this.projects.create(request);

    this.submitting = true;
    save$
      .pipe(
        finalize(() => (this.submitting = false)),
        takeUntil(this.destroy$),
      )
      .subscribe({
        next: (project) => {
          this.form.markAsPristine();
          this.toast.success(this.isEdit ? 'Proje güncellendi.' : `"${project.name}" oluşturuldu.`);
          void this.router.navigate(['/projects', project.id]);
        },
        error: (error: unknown) => this.handleError(error),
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private setProject(project: Project): void {
    this.project = project;
    this.form.reset({ name: project.name, description: project.description ?? '' });
    if (!project.canManage) {
      this.lockedReason = 'Bu projeyi yalnızca proje sahibi veya yönetici düzenleyebilir.';
    } else if (project.status === 'ARCHIVED') {
      this.lockedReason = 'Arşivlenmiş proje düzenlenemez. Önce proje listesinden arşivden çıkar.';
    }
    if (this.lockedReason) {
      this.form.disable();
    }
  }

  private handleError(error: unknown): void {
    // Ağ hatası, 5xx ve 403'ü ErrorInterceptor bildirir
    if (error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500 || error.status === 403)) {
      return;
    }
    const apiError = extractApiError(error);
    this.formError = apiError ? applyServerErrors(this.form, apiError) : 'Kaydedilemedi. Lütfen tekrar deneyin.';
  }
}

/** Yalnızca boşluktan oluşan ad: backend `@NotBlank` ile aynı kural. */
function notBlank(control: AbstractControl): { required: true } | null {
  const value: unknown = control.value;
  return typeof value === 'string' && value.length > 0 && value.trim() === '' ? { required: true } : null;
}
