export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH';

/** Kanban kolon sırası. */
export const TASK_STATUSES: readonly TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE'];
export const TASK_PRIORITIES: readonly TaskPriority[] = ['HIGH', 'MEDIUM', 'LOW'];

export const TASK_STATUS_LABELS: Readonly<Record<TaskStatus, string>> = {
  TODO: 'Yapılacak',
  IN_PROGRESS: 'Devam Ediyor',
  DONE: 'Tamamlandı',
};

export const TASK_PRIORITY_LABELS: Readonly<Record<TaskPriority, string>> = {
  HIGH: 'Yüksek',
  MEDIUM: 'Orta',
  LOW: 'Düşük',
};

/** Backend sınırları (docs/06) — formlar aynı kuralı uygular. */
export const TASK_TITLE_MAX = 200;
export const TASK_DESCRIPTION_MAX = 5000;

/**
 * Görev (docs/06 "Görev yanıtı"). Atanan kişinin adı dönmez: proje üye listesinden eşlenir.
 * `canDelete`: sil düğmesi gösterilsin mi (reporter / OWNER / ADMIN).
 */
export interface Task {
  id: number;
  projectId: number;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  /** Kolon içi sıra, 0'dan başlar. */
  position: number;
  assigneeId: number | null;
  reporterId: number;
  /** `yyyy-MM-dd` */
  dueDate: string | null;
  canDelete: boolean;
  createdAt: string;
  updatedAt: string;
}

/** `GET /api/tasks/board`: üç kolon her zaman var, her biri `position`'a göre sıralı. */
export type Board = Record<TaskStatus, Task[]>;

export interface CreateTaskRequest {
  projectId: number;
  title: string;
  description: string | null;
  /** Boşsa MEDIUM. */
  priority?: TaskPriority;
  assigneeId: number | null;
  dueDate: string | null;
}

/** PUT tam güncellemedir: gönderilmeyen `assigneeId`/`dueDate` boşalır. Durum/sıra değişmez. */
export interface UpdateTaskRequest {
  title: string;
  description: string | null;
  priority: TaskPriority;
  assigneeId: number | null;
  dueDate: string | null;
}

/** `GET /api/tasks` sıralanabilir alanları (docs/06 "Liste filtreleri"). */
export type TaskSortField = 'title' | 'status' | 'priority' | 'dueDate' | 'createdAt';
export type SortDirection = 'asc' | 'desc';

export interface TaskSort {
  field: TaskSortField;
  direction: SortDirection;
}

/**
 * `GET /api/tasks` parametreleri. Boş alan gönderilmez. `projectId` yoksa üye olunan tüm projeler.
 * Çok değerli filtreler (status, priority) virgülle birleştirilir.
 */
export interface TaskListParams {
  projectId?: number;
  status?: readonly TaskStatus[];
  priority?: readonly TaskPriority[];
  /** Kullanıcı id'si veya `none` (atanmamış). */
  assigneeId?: number | 'none';
  q?: string;
  /** `yyyy-MM-dd`, dahil */
  dueFrom?: string;
  dueTo?: string;
  overdue?: boolean;
  /** Öncelik sırasına göre; boşsa sunucu varsayılanı (en yeniler önce). */
  sort?: readonly TaskSort[];
  page?: number;
  size?: number;
}

/** Bitiş tarihi bugünden önce ve görev tamamlanmamış. `today`: `yyyy-MM-dd` (yerel gün). */
export function isOverdue(task: Pick<Task, 'dueDate' | 'status'>, today: string): boolean {
  return !!task.dueDate && task.status !== 'DONE' && task.dueDate < today;
}

/** Yerel saate göre bugünün `yyyy-MM-dd` biçimi (toISOString UTC verir, gece yarısı kayar). */
export function localToday(now: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}
