import { ParamMap, Params } from '@angular/router';

import { SortDirection, TaskSort, TaskSortField } from '../../../core/models/task.model';

export const TASK_PAGE_SIZE = 20;

/** Sunucunun varsayılanı da bu: en yeniler önce. URL'de yazılmaz. */
export const DEFAULT_SORT: TaskSort = { field: 'createdAt', direction: 'desc' };

/** Bir sütuna ilk tıklandığında yön: metin/durum artan, öncelik ve oluşturma en yüksek/en yeni önce. */
export const FIRST_DIRECTION: Readonly<Record<TaskSortField, SortDirection>> = {
  title: 'asc',
  status: 'asc',
  priority: 'desc',
  dueDate: 'asc',
  createdAt: 'desc',
};

const SORT_FIELDS = Object.keys(FIRST_DIRECTION) as TaskSortField[];

/**
 * Görev listesinin URL'deki hali: `?sort=dueDate,asc&page=2` (sayfa 1 tabanlı; varsayılanlar yazılmaz).
 * Arayüz tek sütuna göre sıralar (sunucu birden fazlasını destekler). Filtreler 5-D'de eklenir.
 */
export interface TaskListQuery {
  sort: TaskSort;
  /** 0 tabanlı */
  page: number;
}

/** Bozuk/elle yazılmış değerler sessizce varsayılana döner (sunucuya geçersiz istek gitmez). */
export function toTaskListQuery(params: ParamMap): TaskListQuery {
  return { sort: parseSort(params.get('sort')), page: parsePage(params.get('page')) };
}

export function toQueryParams(query: TaskListQuery): Params {
  return {
    sort: isDefaultSort(query.sort) ? null : `${query.sort.field},${query.sort.direction}`,
    page: query.page > 0 ? query.page + 1 : null,
  };
}

export function sameQuery(a: TaskListQuery, b: TaskListQuery): boolean {
  return a.page === b.page && a.sort.field === b.sort.field && a.sort.direction === b.sort.direction;
}

/** Sütun başlığına tıklama: aynı sütunsa yön değişir, başka sütunsa o sütunun ilk yönü. */
export function nextSort(current: TaskSort, field: TaskSortField): TaskSort {
  if (current.field === field) {
    return { field, direction: current.direction === 'asc' ? 'desc' : 'asc' };
  }
  return { field, direction: FIRST_DIRECTION[field] };
}

function isDefaultSort(sort: TaskSort): boolean {
  return sort.field === DEFAULT_SORT.field && sort.direction === DEFAULT_SORT.direction;
}

function parseSort(value: string | null): TaskSort {
  const [field, direction] = (value ?? '').split(',');
  if (!SORT_FIELDS.includes(field as TaskSortField)) {
    return DEFAULT_SORT;
  }
  const dir: SortDirection =
    direction === 'asc' || direction === 'desc' ? direction : FIRST_DIRECTION[field as TaskSortField];
  return { field: field as TaskSortField, direction: dir };
}

function parsePage(value: string | null): number {
  const page = Number(value);
  return Number.isInteger(page) && page >= 1 ? page - 1 : 0;
}
