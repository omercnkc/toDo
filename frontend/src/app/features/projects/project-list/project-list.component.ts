import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { Observable, of, Subject } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, takeUntil, tap } from 'rxjs/operators';

import { extractApiError } from '../../../core/models/api-error.model';
import { Page } from '../../../core/models/page.model';
import { Project, ProjectStatus } from '../../../core/models/project.model';
import { ProjectService } from '../../../core/services/project.service';
import { ToastService } from '../../../core/services/toast.service';

export const PROJECT_PAGE_SIZE = 12;

/** Durum filtresi: URL'de `status` yoksa aktif projeler, `ALL` tümü. */
export type StatusFilter = ProjectStatus | 'ALL';

export interface ProjectListQuery {
  q: string;
  status: StatusFilter;
  /** 0 tabanlı; URL'de 1 tabanlı `page`. */
  page: number;
}

/**
 * Projelerim (`/projects`). USER yalnızca üyesi olduğu projeleri, ADMIN tümünü görür.
 *
 * - Arama, durum ve sayfa URL'de (`?q=web&status=ARCHIVED&page=2`): yenileme/geri tuşunda liste aynı kalır.
 *   Arama 300 ms bekler; yeni arama eski isteği iptal eder.
 * - Düzenle/arşivle düğmeleri yalnızca `canManage` olan projelerde (asıl yetki backend'de).
 */
@Component({
  selector: 'app-project-list',
  templateUrl: './project-list.component.html',
  styleUrls: ['./project-list.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProjectListComponent implements OnInit, OnDestroy {
  readonly search = new FormControl('');
  readonly statusOptions: { value: StatusFilter; label: string }[] = [
    { value: 'ACTIVE', label: 'Aktif' },
    { value: 'ARCHIVED', label: 'Arşiv' },
    { value: 'ALL', label: 'Tümü' },
  ];

  result: Page<Project> | null = null;
  loading = true;
  loadFailed = false;
  query: ProjectListQuery = { q: '', status: 'ACTIVE', page: 0 };
  /** İşlemi süren proje (kartın düğmeleri kilitlenir). */
  pendingId: number | null = null;

  private readonly reload$ = new Subject<void>();
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly projects: ProjectService,
    private readonly toast: ToastService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(
        map(toProjectListQuery),
        distinctUntilChanged((a, b) => a.q === b.q && a.status === b.status && a.page === b.page),
        tap((query) => {
          this.query = query;
          if (this.search.value !== query.q) {
            this.search.setValue(query.q, { emitEvent: false });
          }
        }),
        switchMap((query) => this.fetch(query)),
        takeUntil(this.destroy$),
      )
      .subscribe();

    this.reload$
      .pipe(
        switchMap(() => this.fetch(this.query)),
        takeUntil(this.destroy$),
      )
      .subscribe();

    this.search.valueChanges
      .pipe(
        map((value: string | null) => (value ?? '').trim()),
        debounceTime(300),
        distinctUntilChanged(),
        takeUntil(this.destroy$),
      )
      .subscribe((q) => {
        if (q !== this.query.q) {
          this.navigate({ ...this.query, q, page: 0 }, true);
        }
      });
  }

  /** Aktif sekmede ve aramasız hiç proje yoksa "ilk projeni oluştur" gösterilir. */
  get isFirstRun(): boolean {
    return !!this.result && this.result.totalElements === 0 && !this.query.q && this.query.status === 'ACTIVE';
  }

  setStatus(status: StatusFilter): void {
    if (status !== this.query.status) {
      this.navigate({ ...this.query, status, page: 0 }, false);
    }
  }

  goToPage(page: number): void {
    this.navigate({ ...this.query, page }, false);
  }

  retry(): void {
    this.reload$.next();
  }

  archive(project: Project): void {
    if (!confirm(`"${project.name}" arşivlensin mi? Arşivden istediğin zaman geri alabilirsin.`)) {
      return;
    }
    this.runAction(project, this.projects.archive(project.id), `"${project.name}" arşivlendi.`);
  }

  restore(project: Project): void {
    this.runAction(project, this.projects.restore(project), `"${project.name}" arşivden çıkarıldı.`);
  }

  roleLabel(project: Project): string {
    if (project.myRole === 'OWNER') {
      return 'Sahibi';
    }
    return project.myRole === 'MEMBER' ? 'Üye' : 'Yönetici erişimi';
  }

  trackById(_index: number, project: Project): number {
    return project.id;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private runAction(project: Project, action: Observable<unknown>, doneText: string): void {
    this.pendingId = project.id;
    action.subscribe({
      next: () => {
        this.pendingId = null;
        this.toast.success(doneText);
        this.reload$.next(); // proje bulunduğu filtreden çıkmış olabilir
      },
      error: (error: unknown) => {
        this.pendingId = null;
        // 0/5xx/403'ü ErrorInterceptor gösterir
        if (error instanceof HttpErrorResponse && error.status > 0 && error.status < 500 && error.status !== 403) {
          this.toast.error(extractApiError(error)?.message ?? 'İşlem tamamlanamadı.');
        }
        this.cdr.markForCheck();
      },
    });
  }

  private fetch(query: ProjectListQuery): Observable<unknown> {
    this.loading = true;
    this.loadFailed = false;
    this.cdr.markForCheck();
    const status = query.status === 'ALL' ? null : query.status;
    return this.projects.list({ q: query.q, status, page: query.page, size: PROJECT_PAGE_SIZE }).pipe(
      tap((result) => {
        this.loading = false;
        // Son sayfadaki tek proje arşivlendiyse vb.: boş bir sayfada kalma
        if (result.content.length === 0 && query.page > 0 && result.totalPages > 0) {
          this.navigate({ ...query, page: result.totalPages - 1 }, true);
          return;
        }
        this.result = result;
        this.cdr.markForCheck();
      }),
      catchError(() => {
        this.loading = false;
        this.loadFailed = true;
        this.cdr.markForCheck();
        return of(null);
      }),
    );
  }

  private navigate(query: ProjectListQuery, replaceUrl: boolean): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: query.q || null,
        status: query.status === 'ACTIVE' ? null : query.status,
        page: query.page > 0 ? query.page + 1 : null,
      },
      replaceUrl,
    });
  }
}

/** URL → sorgu. Bilinmeyen durum aktif, geçersiz sayfa ilk sayfa sayılır. */
export function toProjectListQuery(params: ParamMap): ProjectListQuery {
  const page = Number(params.get('page'));
  const status = params.get('status');
  return {
    q: (params.get('q') ?? '').trim(),
    status: status === 'ARCHIVED' || status === 'ALL' ? status : 'ACTIVE',
    page: Number.isInteger(page) && page > 1 ? page - 1 : 0,
  };
}
