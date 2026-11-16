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

/** Bitiş tarihi bugünden önce ve görev tamamlanmamış. `today`: `yyyy-MM-dd` (yerel gün). */
export function isOverdue(task: Pick<Task, 'dueDate' | 'status'>, today: string): boolean {
  return !!task.dueDate && task.status !== 'DONE' && task.dueDate < today;
}

/** Yerel saate göre bugünün `yyyy-MM-dd` biçimi (toISOString UTC verir, gece yarısı kayar). */
export function localToday(now: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}
