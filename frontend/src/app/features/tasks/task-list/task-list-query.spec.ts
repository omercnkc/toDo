import { convertToParamMap } from '@angular/router';

import {
  activeFilterCount,
  DEFAULT_QUERY,
  DEFAULT_SORT,
  EMPTY_FILTERS,
  nextSort,
  sameQuery,
  TaskListQuery,
  toApiParams,
  toggleValue,
  toQueryParams,
  toTaskListQuery,
} from './task-list-query';

describe('task-list-query', () => {
  const parse = (params: Record<string, string>) => toTaskListQuery(convertToParamMap(params));
  /** `toQueryParams` çıktısından yalnızca dolu olanlar (URL'de görünenler). */
  const urlOf = (query: TaskListQuery) =>
    Object.fromEntries(Object.entries(toQueryParams(query)).filter(([, v]) => v !== null));

  it('boş URL: filtre yok, en yeniler önce, ilk sayfa', () => {
    expect(parse({})).toEqual(DEFAULT_QUERY);
    expect(urlOf(DEFAULT_QUERY)).toEqual({});
  });

  it('sort ve 1 tabanlı sayfa okunur; yön yoksa alanın ilk yönü', () => {
    const q = parse({ sort: 'dueDate,desc', page: '3' });
    expect(q.sort).toEqual({ field: 'dueDate', direction: 'desc' });
    expect(q.page).toBe(2);
    expect(parse({ sort: 'priority' }).sort).toEqual({ field: 'priority', direction: 'desc' });
    expect(parse({ sort: 'title' }).sort).toEqual({ field: 'title', direction: 'asc' });
  });

  it('filtreler okunur; çoklu değerler sabit sıraya dizilir', () => {
    const q = parse({
      q: '  rapor ', status: 'DONE,todo', priority: 'LOW,HIGH', assignee: 'me',
      dueFrom: '2026-11-01', dueTo: '2026-11-30', overdue: '1',
    });
    expect(q.filters).toEqual({
      q: 'rapor', status: ['TODO', 'DONE'], priority: ['HIGH', 'LOW'], assignee: 'me',
      dueFrom: '2026-11-01', dueTo: '2026-11-30', overdue: true,
    });
    expect(parse({ assignee: 'none' }).filters.assignee).toBe('none');
    expect(parse({ assignee: '42' }).filters.assignee).toBe('42');
    expect(parse({ overdue: 'true' }).filters.overdue).toBeTrue();
  });

  it('bozuk değerler atılır, ters tarih aralığı düzeltilir', () => {
    const q = parse({
      sort: 'position,asc', page: 'abc', status: 'BITTI,TODO', priority: 'ACIL', assignee: '-3',
      dueFrom: '2026-02-30', dueTo: 'dün', overdue: 'evet', q: 'x'.repeat(250),
    });
    expect(q.sort).toEqual(DEFAULT_SORT);
    expect(q.page).toBe(0);
    expect(q.filters).toEqual({ ...EMPTY_FILTERS, q: 'x'.repeat(200), status: ['TODO'] });
    for (const assignee of ['0', 'abc', '1e3', '01', 'ME ']) {
      expect(parse({ assignee }).filters.assignee).withContext(assignee).toBe('');
    }
    const swapped = parse({ dueFrom: '2026-12-01', dueTo: '2026-11-01' }).filters;
    expect([swapped.dueFrom, swapped.dueTo]).toEqual(['2026-11-01', '2026-12-01']);
  });

  it('URL\'ye yalnızca dolu filtreler ve varsayılan olmayanlar yazılır; okuma yazmanın tersi', () => {
    const query: TaskListQuery = {
      filters: { ...EMPTY_FILTERS, q: 'rapor', status: ['TODO', 'IN_PROGRESS'], assignee: 'me', overdue: true },
      sort: { field: 'dueDate', direction: 'asc' },
      page: 4,
    };
    const url = urlOf(query);
    expect(url).toEqual({ q: 'rapor', status: 'TODO,IN_PROGRESS', assignee: 'me', overdue: 1, sort: 'dueDate,asc', page: 5 });
    const back = parse(Object.fromEntries(Object.entries(url).map(([k, v]) => [k, String(v)])));
    expect(back).toEqual(query);
    expect(sameQuery(back, query)).toBeTrue();
  });

  it('API parametreleri: "bana atanan" kullanıcı id\'sine çevrilir; kimlik yoksa istek yok', () => {
    const query = parse({ assignee: 'me', status: 'DONE', q: 'a', dueTo: '2026-12-31', overdue: '1' });
    expect(toApiParams(query, 7, 5)).toEqual({
      projectId: 7, q: 'a', status: ['DONE'], assigneeId: 5, dueTo: '2026-12-31', overdue: true,
      sort: [DEFAULT_SORT], page: 0, size: 20,
    });
    expect(toApiParams(DEFAULT_QUERY, 7, 5)).withContext('boş filtre anahtarı yok').toEqual({
      projectId: 7, sort: [DEFAULT_SORT], page: 0, size: 20,
    });
    expect(toApiParams(query, 7, null)).toBeNull();
    expect(toApiParams(parse({ assignee: 'none' }), 7, null)?.assigneeId).toBe('none');
    expect(toApiParams(parse({ assignee: '42' }), 7, null)?.assigneeId).toBe(42);
    expect(toApiParams(parse({}), 7, null)?.assigneeId).toBeUndefined();
  });

  it('açık filtre sayısı: tarih aralığı tek filtre', () => {
    expect(activeFilterCount(EMPTY_FILTERS)).toBe(0);
    expect(activeFilterCount(parse({ q: 'a', status: 'TODO,DONE', dueFrom: '2026-01-01', dueTo: '2026-02-01', overdue: '1' }).filters)).toBe(4);
  });

  it('çoklu seçim aç/kapa sabit sırada', () => {
    const order = ['TODO', 'IN_PROGRESS', 'DONE'];
    expect(toggleValue(['DONE'], 'TODO', order)).toEqual(['TODO', 'DONE']);
    expect(toggleValue(['TODO', 'DONE'], 'TODO', order)).toEqual(['DONE']);
  });

  it('sütun tıklaması: aynı sütunda yön değişir, yeni sütunda ilk yön', () => {
    expect(nextSort(DEFAULT_SORT, 'createdAt')).toEqual({ field: 'createdAt', direction: 'asc' });
    expect(nextSort(DEFAULT_SORT, 'priority')).toEqual({ field: 'priority', direction: 'desc' });
    expect(nextSort({ field: 'title', direction: 'asc' }, 'title')).toEqual({ field: 'title', direction: 'desc' });
    expect(nextSort({ field: 'title', direction: 'desc' }, 'dueDate')).toEqual({ field: 'dueDate', direction: 'asc' });
  });

  it('sameQuery', () => {
    expect(sameQuery(parse({ sort: 'title' }), parse({ sort: 'title,asc' }))).toBeTrue();
    expect(sameQuery(parse({ status: 'DONE,TODO' }), parse({ status: 'TODO,DONE' }))).toBeTrue();
    expect(sameQuery(parse({ page: '2' }), parse({}))).toBeFalse();
    expect(sameQuery(parse({ q: 'a' }), parse({}))).toBeFalse();
  });
});
