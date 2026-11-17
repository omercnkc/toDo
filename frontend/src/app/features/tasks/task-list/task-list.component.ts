import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { combineLatest, forkJoin, Observable, of, Subject } from 'rxjs';
import { catchError, distinctUntilChanged, map, switchMap, takeUntil, tap } from 'rxjs/operators';

import { Page } from '../../../core/models/page.model';
import { displayName, Project, ProjectMember } from '../../../core/models/project.model';
import {
  isOverdue,
  localToday,
  Task,
  TASK_PRIORITY_LABELS,
  TASK_STATUS_LABELS,
  TaskSort,
  TaskSortField,
} from '../../../core/models/task.model';
import { ProjectService } from '../../../core/services/project.service';
import { TaskService } from '../../../core/services/task.service';
import {
  DEFAULT_SORT,
  nextSort,
  sameQuery,
  TASK_PAGE_SIZE,
  TaskListQuery,
  toQueryParams,
  toTaskListQuery,
} from './task-list-query';

export type TaskListError = 'notFound' | 'forbidden' | 'failed';

interface SortOption {
  label: string;
  sort: TaskSort;
}

/**
 * Görev listesi (`/projects/:id/tasks`, docs/08): sunucu tarafı sıralama ve sayfalama.
 *
 * - Sıralama ve sayfa URL'de (`?sort=dueDate,asc&page=2`): yenileme, geri tuşu ve paylaşılan bağlantı aynı
 *   listeyi açar. Yeni sorgu eski isteği iptal eder (`switchMap`).
 * - Masaüstünde tablo (sütun başlığına tıklayınca sıralar, `aria-sort`), mobilde kart + sıralama seçimi.
 * - Satıra tıklayınca (veya Enter) Kanban'daki görev modalı açılır; değişiklikten sonra aynı sayfa yeniden
 *   yüklenir (sayfa boşaldıysa son sayfaya geçilir).
 * - Yeni sayfa yüklenirken eski sonuç ekranda kalır (titreme yok), üstte küçük bir yükleniyor göstergesi.
 */
@Component({
  selector: 'app-task-list',
  templateUrl: './task-list.component.html',
  styleUrls: ['./task-list.component.scss'],
})
export class TaskListComponent implements OnInit, OnDestroy {
  readonly statusLabels = TASK_STATUS_LABELS;
  readonly priorityLabels = TASK_PRIORITY_LABELS;
  readonly today = localToday();
  readonly columns: { field: TaskSortField; label: string }[] = [
    { field: 'title', label: 'Başlık' },
    { field: 'status', label: 'Durum' },
    { field: 'priority', label: 'Öncelik' },
    { field: 'dueDate', label: 'Bitiş' },
    { field: 'createdAt', label: 'Oluşturulma' },
  ];
  /** Mobil sıralama seçimi (tablo başlığı yok). */
  readonly sortOptions: SortOption[] = [
    { label: 'En yeni', sort: { field: 'createdAt', direction: 'desc' } },
    { label: 'En eski', sort: { field: 'createdAt', direction: 'asc' } },
    { label: 'Bitiş tarihi (yakın)', sort: { field: 'dueDate', direction: 'asc' } },
    { label: 'Bitiş tarihi (uzak)', sort: { field: 'dueDate', direction: 'desc' } },
    { label: 'Öncelik (yüksek)', sort: { field: 'priority', direction: 'desc' } },
    { label: 'Öncelik (düşük)', sort: { field: 'priority', direction: 'asc' } },
    { label: 'Başlık (A–Z)', sort: { field: 'title', direction: 'asc' } },
    { label: 'Başlık (Z–A)', sort: { field: 'title', direction: 'desc' } },
    { label: 'Durum', sort: { field: 'status', direction: 'asc' } },
  ];

  projectId = 0;
  project: Project | null = null;
  members: ProjectMember[] = [];
  result: Page<Task> | null = null;
  query: TaskListQuery = { sort: DEFAULT_SORT, page: 0 };
  loading = true;
  error: TaskListError | null = null;

  /** Modal: `undefined` kapalı, `null` yeni görev, görev → ayrıntı/düzenleme. */
  modalTask: Task | null | undefined = undefined;

  private membersById = new Map<number, ProjectMember>();
  private readonly reload$ = new Subject<void>();
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly tasks: TaskService,
    private readonly projects: ProjectService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    // :id bir üst route'ta (projects/:id → tasks)
    const projectId$ = (this.route.parent ?? this.route).paramMap.pipe(
      map((params) => Number(params.get('id'))),
      distinctUntilChanged(),
    );
    const query$ = this.route.queryParamMap.pipe(map(toTaskListQuery), distinctUntilChanged(sameQuery));

    combineLatest([projectId$, query$])
      .pipe(
        switchMap(([id, query]) => this.load(id, query)),
        takeUntil(this.destroy$),
      )
      .subscribe();

    this.reload$
      .pipe(
        switchMap(() => this.load(this.projectId, this.query)),
        takeUntil(this.destroy$),
      )
      .subscribe();
  }

  get readOnly(): boolean {
    return this.project?.status === 'ARCHIVED';
  }

  /** Tablo başlığının `aria-sort` değeri. */
  ariaSort(field: TaskSortField): 'ascending' | 'descending' | 'none' {
    if (this.query.sort.field !== field) {
      return 'none';
    }
    return this.query.sort.direction === 'asc' ? 'ascending' : 'descending';
  }

  sortBy(field: TaskSortField): void {
    this.navigate({ sort: nextSort(this.query.sort, field), page: 0 });
  }

  /** Mobil seçim: değer `sortOptions` index'i. */
  selectSort(index: string): void {
    const option = this.sortOptions[Number(index)];
    if (option) {
      this.navigate({ sort: option.sort, page: 0 });
    }
  }

  get selectedSortIndex(): number {
    return this.sortOptions.findIndex(
      (o) => o.sort.field === this.query.sort.field && o.sort.direction === this.query.sort.direction,
    );
  }

  goToPage(page: number): void {
    this.navigate({ ...this.query, page });
  }

  assigneeName(task: Task): string | null {
    if (task.assigneeId === null) {
      return null;
    }
    const member = this.membersById.get(task.assigneeId);
    return member ? displayName(member, member.userId) : `Kullanıcı #${task.assigneeId}`;
  }

  overdue(task: Task): boolean {
    return isOverdue(task, this.today);
  }

  /** "41–60 / 135" */
  get rangeText(): string {
    if (!this.result || this.result.totalElements === 0) {
      return '';
    }
    const start = this.result.page * this.result.size + 1;
    const end = start + this.result.content.length - 1;
    return `${start}–${end} / ${this.result.totalElements}`;
  }

  // ---------- modal ----------

  openNew(): void {
    this.modalTask = null;
  }

  open(task: Task): void {
    this.modalTask = task;
  }

  closeModal(): void {
    this.modalTask = undefined;
  }

  onTaskChanged(): void {
    this.reload$.next();
  }

  retry(): void {
    this.reload$.next();
  }

  trackTask(_index: number, task: Task): number {
    return task.id;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private navigate(query: TaskListQuery): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: toQueryParams(query) });
  }

  /** Proje bağlamı (proje + üyeler) yalnızca proje değişince; liste her sorguda. */
  private load(id: number, query: TaskListQuery): Observable<unknown> {
    this.projectId = id;
    this.query = query;
    this.error = null;
    if (!Number.isInteger(id) || id <= 0) {
      this.loading = false;
      this.error = 'notFound';
      return of(null);
    }
    this.loading = true;
    const context$: Observable<unknown> =
      this.project?.id === id
        ? of(null)
        : forkJoin({
            project: this.projects.get(id, true),
            members: this.projects.members(id).pipe(catchError(() => of([] as ProjectMember[]))),
          }).pipe(
            tap(({ project, members }) => {
              this.project = project;
              this.members = members;
              this.membersById = new Map(members.map((member) => [member.userId, member]));
            }),
          );

    return context$.pipe(
      switchMap(() =>
        this.tasks.list(
          { projectId: id, sort: [query.sort], page: query.page, size: TASK_PAGE_SIZE },
          true,
        ),
      ),
      tap((result) => {
        // Son sayfadaki görev silindiyse vb.: boş bir sayfada kalma
        if (result.content.length === 0 && query.page > 0 && result.totalPages > 0) {
          this.navigate({ ...query, page: result.totalPages - 1 });
          return;
        }
        this.result = result;
        this.loading = false;
      }),
      catchError((error: unknown) => {
        this.loading = false;
        this.result = null;
        this.error = toListError(error);
        if (this.error !== 'failed') {
          this.project = null;
        }
        return of(null);
      }),
    );
  }
}

function toListError(error: unknown): TaskListError {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 404 || error.status === 400) {
      return 'notFound';
    }
    if (error.status === 403) {
      return 'forbidden';
    }
  }
  return 'failed';
}
