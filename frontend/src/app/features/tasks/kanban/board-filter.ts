import { Board, Task, TASK_STATUSES, TaskPriority } from '../../../core/models/task.model';

/**
 * Pano filtresi (istemci tarafı; sunucu tarafı arama/filtre/sayfalama Faz 5'te liste görünümünde).
 *
 * @param assignee '' = hepsi, 'me' = bana atanan, 'none' = atanmamış, diğer = kullanıcı id'si
 */
export interface BoardFilter {
  search: string;
  assignee: string;
  priority: TaskPriority | '';
}

export const EMPTY_FILTER: BoardFilter = { search: '', assignee: '', priority: '' };

export function isFilterActive(filter: BoardFilter): boolean {
  return filter.search.trim() !== '' || filter.assignee !== '' || filter.priority !== '';
}

/** Görünür kartlar; filtre yoksa aynı board nesnesi döner. Arama başlık + açıklamada, Türkçe büyük/küçük harf duyarsız. */
export function filterBoard(board: Board, filter: BoardFilter, currentUserId: number | null): Board {
  if (!isFilterActive(filter)) {
    return board;
  }
  const query = filter.search.trim().toLocaleLowerCase('tr-TR');
  const matches = (task: Task): boolean => {
    if (filter.priority && task.priority !== filter.priority) {
      return false;
    }
    // Kimlik bilinmiyorsa "bana atanan" boş: null === null atanmamışları eşleştirmesin
    if (filter.assignee === 'me' && (currentUserId === null || task.assigneeId !== currentUserId)) {
      return false;
    }
    if (filter.assignee === 'none' && task.assigneeId !== null) {
      return false;
    }
    if (filter.assignee && filter.assignee !== 'me' && filter.assignee !== 'none'
      && task.assigneeId !== Number(filter.assignee)) {
      return false;
    }
    if (query) {
      const text = `${task.title} ${task.description ?? ''}`.toLocaleLowerCase('tr-TR');
      return text.includes(query);
    }
    return true;
  };
  const result = {} as Board;
  for (const status of TASK_STATUSES) {
    result[status] = board[status].filter(matches);
  }
  return result;
}
