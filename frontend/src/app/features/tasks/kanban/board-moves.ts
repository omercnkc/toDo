import { Board, Task, TASK_STATUSES, TaskStatus } from '../../../core/models/task.model';

/** Board'daki bir yer: kolon + kolon içi sıra. */
export interface BoardSlot {
  status: TaskStatus;
  index: number;
}

/** Board'un derin olmayan kopyası (kolon dizileri ve görev nesneleri yeni): geri alma için anlık görüntü. */
export function cloneBoard(board: Board): Board {
  const copy = {} as Board;
  for (const status of TASK_STATUSES) {
    copy[status] = (board[status] ?? []).map((task) => ({ ...task }));
  }
  return copy;
}

/**
 * Sürükle-bırakın yerel (iyimser) karşılığı; sunucudaki `move` ile aynı kural (docs/05 "Sıralama Stratejisi"):
 * görev hedef kolonun `to.index` sırasına girer, aradakiler kayar, etkilenen kolonların `position`'ları
 * 0..n-1 olarak yeniden yazılır. Hedef sıra kolon boyunu aşarsa sona sınırlanır.
 *
 * Saf fonksiyon: girdi değişmez, yeni board döner. Değişiklik yoksa (aynı yer) `null`.
 */
export function moveTask(board: Board, from: BoardSlot, to: BoardSlot): Board | null {
  const source = board[from.status];
  const task = source?.[from.index];
  if (!task) {
    return null;
  }
  const sameColumn = from.status === to.status;
  const targetLength = sameColumn ? source.length - 1 : board[to.status].length;
  const index = Math.max(0, Math.min(to.index, targetLength));
  if (sameColumn && index === from.index) {
    return null;
  }

  const next: Board = { ...board };
  const newSource = [...source];
  newSource.splice(from.index, 1);
  const newTarget = sameColumn ? newSource : [...board[to.status]];
  newTarget.splice(index, 0, { ...task, status: to.status });

  next[from.status] = renumber(newSource, from.status);
  if (!sameColumn) {
    next[to.status] = renumber(newTarget, to.status);
  }
  return next;
}

/** Kolondaki görevlerin `status`/`position` alanlarını sıraya göre yazar (değişmeyen görev nesnesi korunur). */
function renumber(tasks: Task[], status: TaskStatus): Task[] {
  return tasks.map((task, position) =>
    task.position === position && task.status === status ? task : { ...task, position, status },
  );
}

/** Kolonu ve sırasıyla görevi bulur. */
export function findSlot(board: Board, taskId: number): BoardSlot | null {
  for (const status of TASK_STATUSES) {
    const index = board[status].findIndex((task) => task.id === taskId);
    if (index >= 0) {
      return { status, index };
    }
  }
  return null;
}
