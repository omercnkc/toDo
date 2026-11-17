import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ChartConfiguration } from 'chart.js';
import { merge, Observable, of, Subject } from 'rxjs';
import { catchError, distinctUntilChanged, map, switchMap, takeUntil, tap } from 'rxjs/operators';

import { AuthService } from '../../core/auth/auth.service';
import { DashboardScope, DashboardSummary, ProjectProgress } from '../../core/models/dashboard.model';
import { isOverdue, localToday, Task, TASK_STATUS_LABELS, TaskStatus } from '../../core/models/task.model';
import { DashboardService } from '../../core/services/dashboard.service';
import { TaskService } from '../../core/services/task.service';
import { assignedToMeList, DashboardList, UPCOMING_DAYS, upcomingList } from './dashboard-lists';
import { InvitationService } from '../../core/services/invitation.service';

export type DashboardError = 'unavailable' | 'forbidden' | 'failed';

/** Grafikte ve açıklama listesinde aynı renkler (Bootstrap secondary / primary / success). */
export const STATUS_COLORS: Readonly<Record<TaskStatus, string>> = {
  TODO: '#6c757d',
  IN_PROGRESS: '#0d6efd',
  DONE: '#198754',
};

/** Panelde gösterilen en fazla proje (fazlası için "Tüm projeler"). */
export const MAX_PROJECTS = 8;

interface StatusSlice {
  status: TaskStatus;
  label: string;
  count: number;
  percent: number;
  color: string;
}

/**
 * Panel (`/dashboard`, docs/08 "Dashboard"): özet kartları, durum dağılımı (doughnut), proje ilerlemeleri.
 *
 * - Kapsam URL'de: `?scope=all` yalnızca ADMIN'de "Tüm sistem" anlamına gelir; USER'da yok sayılır (sunucu da
 *   403 verir). Geçiş bağlantıdır (geri tuşu çalışır).
 * - Sayılar sunucuda 30 sn önbelleklenir; "Güncellendi" saati `generatedAt`'tir.
 * - Grafiğin metin karşılığı yanındaki açıklama listesi (sayı + yüzde); tuval ekran okuyucuya tek cümleyle tanıtılır.
 * - Bekleyen davet uyarısı korunur (sayı navbar'daki `InvitationService`'ten, ayrı istek yok).
 * - "Benim projelerim"de iki liste (6-E): bana atanan açık görevler ve önümüzdeki 7 günde bitecekler; projeler arası
 *   `GET /tasks` ucuyla, özetle paralel. Liste hatası yalnızca o kartı etkiler. Görev, projesinin görev listesini
 *   panelde görülen filtreyle açar. Tüm sistem kapsamında gösterilmez (listeler kişisel).
 */
@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
})
export class DashboardComponent implements OnInit, OnDestroy {
  readonly user$ = this.auth.currentUser$;
  readonly pendingInvitations$ = this.invitations.pendingCount$;
  readonly isAdmin = this.auth.currentUser?.role === 'ADMIN';
  readonly maxProjects = MAX_PROJECTS;

  scope: DashboardScope = 'mine';
  summary: DashboardSummary | null = null;
  loading = true;
  error: DashboardError | null = null;

  readonly today = localToday();
  /** Panel listeleri; `null` yüklenmedi/yükleniyor. */
  readonly lists: Record<ListKey, ListState> = { assigned: emptyList(), upcoming: emptyList() };
  readonly listViews: ListView[] = [
    { key: 'assigned', title: 'Bana atanan açık görevler', empty: 'Sana atanmış açık görev yok.', state: this.lists.assigned },
    {
      key: 'upcoming',
      title: 'Yaklaşan bitiş tarihleri',
      hint: `Önümüzdeki ${UPCOMING_DAYS} gün, gecikmişler hariç`,
      empty: 'Önümüzdeki günlerde bitecek açık görev yok.',
      state: this.lists.upcoming,
    },
  ];
  readonly statusLabels = TASK_STATUS_LABELS;
  /** Bir kez hesaplanır: şablondaki bağlantıların `queryParams`'ı her turda yeni nesne olmasın. */
  private readonly listDefs: Record<ListKey, DashboardList> = {
    assigned: assignedToMeList(this.auth.currentUser?.id ?? 0),
    upcoming: upcomingList(this.today),
  };

  slices: StatusSlice[] = [];
  chartData: ChartConfiguration<'doughnut'>['data'] = { labels: [], datasets: [] };
  readonly chartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '62%',
    // "Hareketi azalt" tercihinde animasyon yok
    animation: prefersReducedMotion() ? false : undefined,
    plugins: {
      // Açıklama ve sayılar grafiğin yanında HTML olarak (erişilebilir, mobilde okunaklı)
      legend: { display: false },
      tooltip: { callbacks: { label: (item) => ` ${item.label}: ${item.formattedValue}` } },
    },
  };

  private readonly reload$ = new Subject<void>();
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly auth: AuthService,
    private readonly invitations: InvitationService,
    private readonly dashboard: DashboardService,
    private readonly tasks: TaskService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    const scope$ = this.route.queryParamMap.pipe(
      map((params): DashboardScope => (this.isAdmin && params.get('scope') === 'all' ? 'all' : 'mine')),
      distinctUntilChanged(),
    );
    merge(scope$, this.reload$.pipe(map(() => this.scope)))
      .pipe(
        switchMap((scope) => merge(this.load(scope), scope === 'mine' ? this.loadLists() : of(null))),
        takeUntil(this.destroy$),
      )
      .subscribe();
  }

  setScope(scope: DashboardScope): void {
    if (scope !== this.scope) {
      void this.router.navigate([], { relativeTo: this.route, queryParams: { scope: scope === 'all' ? 'all' : null } });
    }
  }

  retry(): void {
    this.reload$.next();
  }

  get visibleProjects(): ProjectProgress[] {
    return this.summary ? this.summary.projects.slice(0, MAX_PROJECTS) : [];
  }

  get hiddenProjectCount(): number {
    return this.summary ? Math.max(0, this.summary.projects.length - MAX_PROJECTS) : 0;
  }

  /** "Ekranda 12 görev: Yapılacak 3, …" — tuvalin ekran okuyucu karşılığı. */
  get chartLabel(): string {
    const parts = this.slices.map((s) => `${s.label} ${s.count}`).join(', ');
    return `Görev durum dağılımı: ${parts}`;
  }

  roleLabel(project: ProjectProgress): string | null {
    if (project.myRole === 'OWNER') {
      return 'Sahibi';
    }
    return project.myRole === 'MEMBER' ? 'Üye' : null;
  }

  trackProject(_index: number, project: ProjectProgress): number {
    return project.id;
  }

  trackTask(_index: number, task: Task): number {
    return task.id;
  }

  projectName(task: Task): string {
    return this.summary?.projects.find((p) => p.id === task.projectId)?.name ?? `Proje #${task.projectId}`;
  }

  overdue(task: Task): boolean {
    return isOverdue(task, this.today);
  }

  /** Görevin projesindeki görev listesi, panel listesiyle aynı filtreyle. */
  listLink(key: ListKey): DashboardList['link'] {
    return this.listDefs[key].link;
  }



  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** İki liste paralel; her biri kendi hatasını tutar. Kimlik bilinmiyorsa "bana atanan" boş (başkalarını değil). */
  private loadLists(): Observable<unknown> {
    const defs = this.listDefs;
    return merge(
      ...(Object.keys(defs) as ListKey[]).map((key) => {
        const state = this.lists[key];
        state.loading = true;
        state.failed = false;
        if (key === 'assigned' && !this.auth.currentUser) {
          state.loading = false;
          state.tasks = [];
          state.total = 0;
          return of(null);
        }
        return this.tasks.list(defs[key].api, true).pipe(
          tap((page) => {
            state.tasks = page.content;
            state.total = page.totalElements;
            state.loading = false;
          }),
          catchError(() => {
            state.loading = false;
            state.failed = true;
            return of(null);
          }),
        );
      }),
    );
  }

  private load(scope: DashboardScope): Observable<unknown> {
    this.scope = scope;
    this.loading = true;
    this.error = null;
    return this.dashboard.summary(scope).pipe(
      tap((summary) => {
        this.summary = summary;
        this.updateChart(summary);
        this.loading = false;
      }),
      catchError((error: unknown) => {
        this.loading = false;
        this.summary = null;
        this.error = toDashboardError(error);
        return of(null);
      }),
    );
  }

  private updateChart(summary: DashboardSummary): void {
    const counts: Record<TaskStatus, number> = {
      TODO: summary.taskTotals.todo,
      IN_PROGRESS: summary.taskTotals.inProgress,
      DONE: summary.taskTotals.done,
    };
    const total = summary.totalTasks;
    this.slices = (Object.keys(counts) as TaskStatus[]).map((status) => ({
      status,
      label: TASK_STATUS_LABELS[status],
      count: counts[status],
      percent: total ? Math.round((counts[status] * 100) / total) : 0,
      color: STATUS_COLORS[status],
    }));
    this.chartData = {
      labels: this.slices.map((s) => s.label),
      datasets: [
        {
          data: this.slices.map((s) => s.count),
          backgroundColor: this.slices.map((s) => s.color),
          hoverBackgroundColor: this.slices.map((s) => s.color),
          borderWidth: 2,
        },
      ],
    };
  }
}

type ListKey = 'assigned' | 'upcoming';

interface ListState {
  tasks: Task[] | null;
  total: number;
  loading: boolean;
  failed: boolean;
}

interface ListView {
  key: ListKey;
  title: string;
  hint?: string;
  empty: string;
  state: ListState;
}

function emptyList(): ListState {
  return { tasks: null, total: 0, loading: false, failed: false };
}

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
}

function toDashboardError(error: unknown): DashboardError {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0 || error.status === 503 || error.status === 502 || error.status === 504) {
      return 'unavailable';
    }
    if (error.status === 403) {
      return 'forbidden';
    }
  }
  return 'failed';
}
