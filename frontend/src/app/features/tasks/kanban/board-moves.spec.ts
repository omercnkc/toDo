import { Board, Task, TaskStatus } from '../../../core/models/task.model';
import { cloneBoard, findSlot, moveTask } from './board-moves';

function task(id: number, status: TaskStatus, position: number): Task {
  return {
    id, projectId: 1, title: `T${id}`, description: null, status, priority: 'MEDIUM', position,
    assigneeId: null, reporterId: 1, dueDate: null, canDelete: true, createdAt: '', updatedAt: '',
  };
}

/** TODO: 1 2 3 4 · IN_PROGRESS: 5 6 · DONE: — */
function seed(): Board {
  return {
    TODO: [task(1, 'TODO', 0), task(2, 'TODO', 1), task(3, 'TODO', 2), task(4, 'TODO', 3)],
    IN_PROGRESS: [task(5, 'IN_PROGRESS', 0), task(6, 'IN_PROGRESS', 1)],
    DONE: [],
  };
}

/** Kolon → id'ler; ayrıca her kolonda status/position'ın tutarlı olduğunu doğrular. */
function ids(board: Board): Record<TaskStatus, number[]> {
  const result = {} as Record<TaskStatus, number[]>;
  (Object.keys(board) as TaskStatus[]).forEach((status) => {
    board[status].forEach((t, index) => {
      expect(t.status).withContext(`${t.id} durumu`).toBe(status);
      expect(t.position).withContext(`${t.id} sırası`).toBe(index);
    });
    result[status] = board[status].map((t) => t.id);
  });
  return result;
}

describe('board-moves', () => {
  it('kolon içinde aşağı ve yukarı taşır, sıraları yeniden yazar', () => {
    const down = moveTask(seed(), { status: 'TODO', index: 0 }, { status: 'TODO', index: 2 })!;
    expect(ids(down).TODO).toEqual([2, 3, 1, 4]);

    const up = moveTask(down, { status: 'TODO', index: 3 }, { status: 'TODO', index: 0 })!;
    expect(ids(up).TODO).toEqual([4, 2, 3, 1]);
  });

  it('kolonlar arasında: kaynak boşluk kapanır, hedefte yer açılır', () => {
    const next = moveTask(seed(), { status: 'TODO', index: 1 }, { status: 'IN_PROGRESS', index: 1 })!;

    expect(ids(next)).toEqual({ TODO: [1, 3, 4], IN_PROGRESS: [5, 2, 6], DONE: [] });
  });

  it('boş kolona ve kolon boyunu aşan sıraya (sona sınırlanır)', () => {
    const toEmpty = moveTask(seed(), { status: 'IN_PROGRESS', index: 0 }, { status: 'DONE', index: 5 })!;
    expect(ids(toEmpty).DONE).toEqual([5]);

    const beyond = moveTask(seed(), { status: 'TODO', index: 0 }, { status: 'TODO', index: 99 })!;
    expect(ids(beyond).TODO).toEqual([2, 3, 4, 1]);
  });

  it('aynı yere bırakma veya olmayan görev değişiklik sayılmaz', () => {
    expect(moveTask(seed(), { status: 'TODO', index: 2 }, { status: 'TODO', index: 2 })).toBeNull();
    expect(moveTask(seed(), { status: 'DONE', index: 0 }, { status: 'TODO', index: 0 })).toBeNull();
  });

  it('girdiyi değiştirmez; etkilenmeyen kolon ve görevler aynı nesne kalır', () => {
    const board = seed();
    const before = JSON.stringify(board);

    const next = moveTask(board, { status: 'TODO', index: 3 }, { status: 'IN_PROGRESS', index: 0 })!;

    expect(JSON.stringify(board)).toBe(before);
    expect(next.DONE).toBe(board.DONE);
    expect(next.TODO[0]).toBe(board.TODO[0]); // sırası değişmeyen görev kopyalanmaz
  });

  it('findSlot ve cloneBoard', () => {
    const board = seed();
    expect(findSlot(board, 6)).toEqual({ status: 'IN_PROGRESS', index: 1 });
    expect(findSlot(board, 99)).toBeNull();

    const copy = cloneBoard(board);
    expect(copy).toEqual(board);
    expect(copy.TODO).not.toBe(board.TODO);
    expect(copy.TODO[0]).not.toBe(board.TODO[0]);
  });
});
