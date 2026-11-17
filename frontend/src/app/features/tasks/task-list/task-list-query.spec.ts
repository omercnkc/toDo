import { convertToParamMap } from '@angular/router';

import { DEFAULT_SORT, nextSort, sameQuery, toQueryParams, toTaskListQuery } from './task-list-query';

describe('task-list-query', () => {
  const parse = (params: Record<string, string>) => toTaskListQuery(convertToParamMap(params));

  it('boş URL: en yeniler önce, ilk sayfa', () => {
    expect(parse({})).toEqual({ sort: DEFAULT_SORT, page: 0 });
  });

  it('sort ve 1 tabanlı sayfa okunur; yön yoksa alanın ilk yönü', () => {
    expect(parse({ sort: 'dueDate,desc', page: '3' })).toEqual({ sort: { field: 'dueDate', direction: 'desc' }, page: 2 });
    expect(parse({ sort: 'priority' }).sort).toEqual({ field: 'priority', direction: 'desc' });
    expect(parse({ sort: 'title' }).sort).toEqual({ field: 'title', direction: 'asc' });
  });

  it('bozuk değerler varsayılana döner', () => {
    expect(parse({ sort: 'position,asc' }).sort).toEqual(DEFAULT_SORT);
    expect(parse({ sort: 'title,yukari' }).sort).toEqual({ field: 'title', direction: 'asc' });
    for (const page of ['0', '-2', '1.5', 'abc', '']) {
      expect(parse({ page }).page).withContext(page).toBe(0);
    }
  });

  it('URL\'ye varsayılanlar yazılmaz; okuma ile yazma birbirinin tersi', () => {
    expect(toQueryParams({ sort: DEFAULT_SORT, page: 0 })).toEqual({ sort: null, page: null });
    const query = { sort: { field: 'dueDate' as const, direction: 'asc' as const }, page: 4 };
    const params = toQueryParams(query);
    expect(params).toEqual({ sort: 'dueDate,asc', page: 5 });
    expect(parse({ sort: params['sort'], page: String(params['page']) })).toEqual(query);
  });

  it('sütun tıklaması: aynı sütunda yön değişir, yeni sütunda ilk yön', () => {
    expect(nextSort(DEFAULT_SORT, 'createdAt')).toEqual({ field: 'createdAt', direction: 'asc' });
    expect(nextSort(DEFAULT_SORT, 'priority')).toEqual({ field: 'priority', direction: 'desc' });
    expect(nextSort({ field: 'title', direction: 'asc' }, 'title')).toEqual({ field: 'title', direction: 'desc' });
    expect(nextSort({ field: 'title', direction: 'desc' }, 'dueDate')).toEqual({ field: 'dueDate', direction: 'asc' });
  });

  it('sameQuery', () => {
    expect(sameQuery(parse({ sort: 'title' }), parse({ sort: 'title,asc' }))).toBeTrue();
    expect(sameQuery(parse({ page: '2' }), parse({}))).toBeFalse();
  });
});
