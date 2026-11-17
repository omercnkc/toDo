import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SKIP_ERROR_TOAST } from '../interceptors/http-context.tokens';
import { isOverdue, localToday } from '../models/task.model';
import { TaskService } from './task.service';

describe('TaskService', () => {
  let service: TaskService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(TaskService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('board: projectId parametresi, sessiz modda genel bildirim kapalı', () => {
    service.board(7, true).subscribe();

    const req = http.expectOne((r) => r.url === '/api/tasks/board');
    expect(req.request.params.get('projectId')).toBe('7');
    expect(req.request.context.get(SKIP_ERROR_TOAST)).toBeTrue();
    req.flush({ TODO: [], IN_PROGRESS: [], DONE: [] });
  });

  it('list: boş filtreler gönderilmez, çok değerliler virgüllü, sort tekrarlı', () => {
    service.list({ projectId: 7, page: 0, size: 20, q: '  ', status: [], sort: [] }).subscribe();
    const plain = http.expectOne((r) => r.url === '/api/tasks');
    expect(plain.request.params.keys().sort()).toEqual(['page', 'projectId', 'size']);
    expect(plain.request.context.get(SKIP_ERROR_TOAST)).toBeFalse();
    plain.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });

    service
      .list(
        {
          status: ['TODO', 'DONE'],
          priority: ['HIGH'],
          assigneeId: 'none',
          q: ' rapor ',
          dueFrom: '2026-11-01',
          dueTo: '2026-11-30',
          overdue: true,
          sort: [
            { field: 'priority', direction: 'desc' },
            { field: 'title', direction: 'asc' },
          ],
        },
        true,
      )
      .subscribe();
    const req = http.expectOne((r) => r.url === '/api/tasks');
    const p = req.request.params;
    expect(p.has('projectId')).toBeFalse();
    expect(p.get('status')).toBe('TODO,DONE');
    expect(p.get('priority')).toBe('HIGH');
    expect(p.get('assigneeId')).toBe('none');
    expect(p.get('q')).toBe('rapor');
    expect(p.get('dueFrom')).toBe('2026-11-01');
    expect(p.get('dueTo')).toBe('2026-11-30');
    expect(p.get('overdue')).toBe('true');
    expect(p.getAll('sort')).toEqual(['priority,desc', 'title,asc']);
    expect(req.request.context.get(SKIP_ERROR_TOAST)).toBeTrue();
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('move: PATCH /move {status, position}', () => {
    service.move(42, 'DONE', 3).subscribe();

    const req = http.expectOne('/api/tasks/42/move');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'DONE', position: 3 });
    req.flush({});
  });

  it('oluşturma, düzenleme, durum ve silme uçları', () => {
    service.create({ projectId: 7, title: 'A', description: null, assigneeId: null, dueDate: null }).subscribe();
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/tasks').flush({});

    service.update(5, { title: 'B', description: null, priority: 'LOW', assigneeId: 2, dueDate: '2026-12-01' })
      .subscribe();
    const put = http.expectOne('/api/tasks/5');
    expect(put.request.method).toBe('PUT');
    put.flush({});

    service.changeStatus(5, 'IN_PROGRESS').subscribe();
    const status = http.expectOne('/api/tasks/5/status');
    expect(status.request.body).toEqual({ status: 'IN_PROGRESS' });
    status.flush({});

    service.delete(5).subscribe();
    expect(http.expectOne('/api/tasks/5').request.method).toBe('DELETE');
  });

  it('gecikme: tarihi geçmiş ve tamamlanmamış', () => {
    expect(isOverdue({ dueDate: '2026-11-15', status: 'TODO' }, '2026-11-16')).toBeTrue();
    expect(isOverdue({ dueDate: '2026-11-16', status: 'TODO' }, '2026-11-16')).toBeFalse();
    expect(isOverdue({ dueDate: '2026-11-15', status: 'DONE' }, '2026-11-16')).toBeFalse();
    expect(isOverdue({ dueDate: null, status: 'TODO' }, '2026-11-16')).toBeFalse();
    expect(localToday(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05');
  });
});
