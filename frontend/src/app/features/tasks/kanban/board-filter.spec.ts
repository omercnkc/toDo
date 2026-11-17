import { Board, Task, TaskStatus } from '../../../core/models/task.model';
import { EMPTY_FILTER, filterBoard, isFilterActive } from './board-filter';

function task(id: number, status: TaskStatus, extra: Partial<Task> = {}): Task {
  return {
    id, projectId: 1, title: `Görev ${id}`, description: null, status, priority: 'MEDIUM', position: 0,
    assigneeId: null, reporterId: 1, dueDate: null, canDelete: true, createdAt: '', updatedAt: '', ...extra,
  };
}

const BOARD: Board = {
  TODO: [
    task(1, 'TODO', { title: 'Işık ayarı', priority: 'HIGH', assigneeId: 5 }),
    task(2, 'TODO', { description: 'İSTANBUL ofisi', assigneeId: 6 }),
  ],
  IN_PROGRESS: [task(3, 'IN_PROGRESS', { priority: 'HIGH' })],
  DONE: [],
};

function ids(board: Board): number[] {
  return [...board.TODO, ...board.IN_PROGRESS, ...board.DONE].map((t) => t.id);
}

describe('board-filter', () => {
  it('filtre yoksa aynı board döner', () => {
    expect(isFilterActive(EMPTY_FILTER)).toBeFalse();
    expect(isFilterActive({ ...EMPTY_FILTER, search: '   ' })).toBeFalse();
    expect(filterBoard(BOARD, EMPTY_FILTER, 5)).toBe(BOARD);
  });

  it('öncelik ve atanan (ben / atanmamış / kişi)', () => {
    expect(ids(filterBoard(BOARD, { ...EMPTY_FILTER, priority: 'HIGH' }, 5))).toEqual([1, 3]);
    expect(ids(filterBoard(BOARD, { ...EMPTY_FILTER, assignee: 'me' }, 5))).toEqual([1]);
    expect(ids(filterBoard(BOARD, { ...EMPTY_FILTER, assignee: 'me' }, null))).toEqual([]);
    expect(ids(filterBoard(BOARD, { ...EMPTY_FILTER, assignee: 'none' }, 5))).toEqual([3]);
    expect(ids(filterBoard(BOARD, { ...EMPTY_FILTER, assignee: '6' }, 5))).toEqual([2]);
    expect(ids(filterBoard(BOARD, { ...EMPTY_FILTER, assignee: 'me', priority: 'HIGH' }, 5))).toEqual([1]);
  });

  it('arama başlık ve açıklamada, Türkçe harf duyarsız (I/ı, İ/i)', () => {
    expect(ids(filterBoard(BOARD, { ...EMPTY_FILTER, search: 'ışık' }, 5))).toEqual([1]);
    expect(ids(filterBoard(BOARD, { ...EMPTY_FILTER, search: 'istanbul' }, 5))).toEqual([2]);
    expect(ids(filterBoard(BOARD, { ...EMPTY_FILTER, search: ' görev 3 ' }, 5))).toEqual([3]);
  });
});
