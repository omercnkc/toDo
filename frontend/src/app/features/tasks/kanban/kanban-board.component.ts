import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { forkJoin, Observable, of, Subject } from 'rxjs';
import { catchError, concatMap, map, switchMap, takeUntil } from 'rxjs/operators';

import { AuthService } from '../../../core/auth/auth.service';
import { extractApiError } from '../../../core/models/api-error.model';
import { displayName, Project, ProjectMember } from '../../../core/models/project.model';
import {
  Board,
  isOverdue,
  localToday,
  Task,
  TASK_PRIORITY_LABELS,
  TASK_STATUS_LABELS,
  TASK_STATUSES,
  TASK_PRIORITIES,
  TaskStatus,
} from '../../../core/models/task.model';
import { ProjectService } from '../../../core/services/project.service';
import { TaskService } from '../../../core/services/task.service';
import { ToastService } from '../../../core/services/toast.service';
import { BoardFilter, EMPTY_FILTER, filterBoard, isFilterActive } from './board-filter';
import { cloneBoard, findSlot, moveTask } from './board-moves';

export type BoardError = 'notFound' | 'forbidden' | 'failed';

/** Sunucuya gönderilecek taşıma; hata olursa `snapshot`'a dönülür. */
interface MoveCommand {
  taskId: number;
  status: TaskStatus;
  position: number;
  snapshot: Board;
}

type MoveResult = { command: MoveCommand; task: Task } | { command: MoveCommand; error: unknown };

/**
 * Kanban panosu (`/projects/:id/board`, docs/08 "Kanban").
 *
 * Sürükle-bırak **iyimser**: board hemen güncellenir, sonra `PATCH /tasks/{id}/move` gönderilir.
 * - Taşımalar sırayla gönderilir (`concatMap`): hızlı art arda sürüklemelerde sunucu da aynı sırayı görür.
 * - Hata olursa: bekleyen başka taşıma yoksa board taşımadan önceki hâline döner; her durumda board
 *   sunucudan yeniden yüklenir (gerçek durum sunucudadır) ve bildirim gösterilir.
 * - Taşımalar sürerken gelen yeniden yükleme yanıtı yok sayılır (iyimser durumu ezmesin), kuyruk boşalınca
 *   yeniden yüklenir.
 * Arşivlenmiş projede sürükleme kapalıdır.
 *
 * Filtre (arama, atanan, öncelik) istemci tarafındadır. Filtre açıkken görünen sıra tüm kolonun sırası
 * olmadığından hedef index belirsizdir: sürükleme yalnızca **kolon değiştirir** (görev hedef kolonun sonuna
 * eklenir), kolon içi sıralama filtresiz görünümde yapılır (docs/08 "Kanban").
 *
 * Karta tıklayınca (veya Enter) görev modalı açılır; "Yeni görev" boş modal açar.
 */
@Component({
  selector: 'app-kanban-board',
  templateUrl: './kanban-board.component.html',
  styleUrls: ['./kanban-board.component.scss'],
})
export class KanbanBoardComponent implements OnInit, OnDestroy {
  readonly statuses = TASK_STATUSES;
  readonly statusLabels = TASK_STATUS_LABELS;
  readonly priorityLabels = TASK_PRIORITY_LABELS;
  readonly priorities = TASK_PRIORITIES;
  readonly today = localToday();
  readonly currentUserId = this.auth.currentUser?.id ?? null;

  projectId = 0;
  project: Project | null = null;
  members: ProjectMember[] = [];
  /** Kartlarda gösterilen (filtrelenmiş) board. */
  view: Board | null = null;
  filter: BoardFilter = { ...EMPTY_FILTER };
  loading = true;
  error: BoardError | null = null;
  /** Gönderilmiş ama yanıtı gelmemiş taşıma sayısı. */
  pendingMoves = 0;

  /** Modal: `undefined` kapalı, `null` yeni görev, görev → düzenleme. */
  modalTask: Task | null | undefined = undefined;

  private _board: Board | null = null;
  private membersById = new Map<number, ProjectMember>();
  /** Sürüklemenin sonunda tarayıcının ürettiği tıklama modalı açmasın. */
  private dragging = false;
  private reloadWhenIdle = false;
  private readonly moves$ = new Subject<MoveCommand>();
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly tasks: TaskService,
    private readonly projects: ProjectService,
    private readonly toast: ToastService,
    private readonly route: ActivatedRoute,
    private readonly auth: AuthService,
  ) {}

  /** Tüm görevler (sunucudaki sıra); her atamada görünür board yeniden hesaplanır. */
  get board(): Board | null {
    return this._board;
  }

  set board(board: Board | null) {
    this._board = board;
    this.refreshView();
  }

  ngOnInit(): void {
    // :id bir üst route'ta (projects/:id → board)
    (this.route.parent ?? this.route).paramMap
      .pipe(
        map((params) => Number(params.get('id'))),
        switchMap((id) => {
          this.projectId = id;
          if (!Number.isInteger(id) || id <= 0) {
            this.loading = false;
            this.error = 'notFound';
            return of(null);
          }
          return this.loadAll(id);
        }),
        takeUntil(this.destroy$),
      )
      .subscribe();

    this.moves$
      .pipe(
        concatMap((command) =>
          this.tasks.move(command.taskId, command.status, command.position).pipe(
            map((task): MoveResult => ({ command, task })),
            catchError((error: unknown) => of<MoveResult>({ command, error })),
          ),
        ),
        takeUntil(this.destroy$),
      )
      .subscribe((result) => this.onMoveResult(result));
  }

  get readOnly(): boolean {
    return this.project?.status === 'ARCHIVED';
  }

  get totalTasks(): number {
    return this.board ? this.statuses.reduce((sum, status) => sum + this.board![status].length, 0) : 0;
  }

  dropListId(status: TaskStatus): string {
    return `kanban-${status}`;
  }

  get filterActive(): boolean {
    return isFilterActive(this.filter);
  }

  setFilter(change: Partial<BoardFilter>): void {
    this.filter = { ...this.filter, ...change };
    this.refreshView();
  }

  clearFilter(): void {
    this.filter = { ...EMPTY_FILTER };
    this.refreshView();
  }

  /**
   * CDK `drop`: `container.data` kolonun durumu, `item.data` görev. Görevin gerçek yeri tüm board'da aranır
   * (filtreli görünümdeki index tüm kolonun index'i değildir).
   */
  drop(event: CdkDragDrop<TaskStatus, TaskStatus, Task>): void {
    if (!this.board || this.readOnly) {
      return;
    }
    const from = findSlot(this.board, event.item.data.id);
    const targetStatus = event.container.data;
    if (!from) {
      return;
    }
    let to = { status: targetStatus, index: event.currentIndex };
    if (this.filterActive) {
      if (from.status === targetStatus) {
        return; // filtreli görünümde kolon içi sıralama yok
      }
      to = { status: targetStatus, index: this.board[targetStatus].length };
    }
    const task = this.board[from.status][from.index];
    const next = moveTask(this.board, from, to);
    if (!next) {
      return;
    }
    const snapshot = this.board;
    const slot = findSlot(next, task.id)!;
    this.board = next;
    this.pendingMoves++;
    this.moves$.next({ taskId: task.id, status: slot.status, position: slot.index, snapshot });
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

  // ---------- modal ----------

  openNew(): void {
    this.modalTask = null;
  }

  open(task: Task): void {
    if (!this.dragging) {
      this.modalTask = task;
    }
  }

  closeModal(): void {
    this.modalTask = undefined;
  }

  /** Modal görevi oluşturdu/değiştirdi/sildi. */
  onTaskChanged(): void {
    if (this.pendingMoves === 0) {
      this.reloadBoard();
    } else {
      this.reloadWhenIdle = true;
    }
  }

  onDragStarted(): void {
    this.dragging = true;
  }

  /** Bırakmanın ardından gelen tıklama olayı geçtikten sonra. */
  onDragEnded(): void {
    setTimeout(() => (this.dragging = false));
  }

  retry(): void {
    this.loadAll(this.projectId).pipe(takeUntil(this.destroy$)).subscribe();
  }

  trackTask(_index: number, task: Task): number {
    return task.id;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private refreshView(): void {
    this.view = this._board ? filterBoard(this._board, this.filter, this.currentUserId) : null;
  }

  private onMoveResult(result: MoveResult): void {
    this.pendingMoves--;
    if ('task' in result) {
      // Sunucu başka bir yere koyduysa (arada başkası değiştirdi) board'u sunucudan tazele
      const slot = this.board ? findSlot(this.board, result.task.id) : null;
      if (!slot || slot.status !== result.task.status || slot.index !== result.task.position) {
        this.reloadWhenIdle = true;
      }
    } else {
      this.showMoveError(result.error);
      if (this.pendingMoves === 0) {
        this.board = result.command.snapshot;
      }
      this.reloadWhenIdle = true;
    }
    if (this.pendingMoves === 0 && this.reloadWhenIdle) {
      this.reloadWhenIdle = false;
      this.reloadBoard();
    }
  }

  private showMoveError(error: unknown): void {
    // 0/5xx/403'ü ErrorInterceptor zaten gösterir
    if (error instanceof HttpErrorResponse && error.status > 0 && error.status < 500 && error.status !== 403) {
      this.toast.error(extractApiError(error)?.message ?? 'Görev taşınamadı.');
    } else if (!(error instanceof HttpErrorResponse)) {
      this.toast.error('Görev taşınamadı.');
    }
  }

  /** Yalnızca board; taşıma sürerken gelen yanıt iyimser durumu ezmesin diye o zaman yok sayılır. */
  private reloadBoard(): void {
    this.tasks
      .board(this.projectId, true)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (board) => {
          if (this.pendingMoves === 0) {
            this.board = cloneBoard(board);
          } else {
            this.reloadWhenIdle = true;
          }
        },
        error: () => this.toast.error('Pano yenilenemedi. Sayfayı yenileyip tekrar dene.'),
      });
  }

  /** Proje (erişim kontrolü) + board; üye adları en iyi çabayla (alınamazsa "Kullanıcı #id"). */
  private loadAll(id: number): Observable<unknown> {
    this.loading = !this.board;
    this.error = null;
    return forkJoin({
      project: this.projects.get(id, true),
      board: this.tasks.board(id, true),
      members: this.projects.members(id).pipe(catchError(() => of([] as ProjectMember[]))),
    }).pipe(
      map(({ project, board, members }) => {
        this.project = project;
        this.board = cloneBoard(board);
        this.members = members;
        this.membersById = new Map(members.map((member) => [member.userId, member]));
        this.loading = false;
        return null;
      }),
      catchError((error: unknown) => {
        this.loading = false;
        this.project = null;
        this.board = null;
        this.error = toBoardError(error);
        return of(null);
      }),
    );
  }
}

function toBoardError(error: unknown): BoardError {
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
