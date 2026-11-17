import { ParamMap, Params } from '@angular/router';

import {
  SortDirection,
  TASK_PRIORITIES,
  TASK_STATUSES,
  TASK_TITLE_MAX,
  TaskListParams,
  TaskPriority,
  TaskSort,
  TaskSortField,
  TaskStatus,
} from '../../../core/models/task.model';

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
const DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Atanan filtresi: '' herkes, 'me' bana atanan, 'none' atanmamış, aksi halde kullanıcı id'si (metin). */
export type AssigneeFilter = '' | 'me' | 'none' | `${number}`;

export interface TaskFilters {
  /** Başlık veya açıklamada arama (sunucu Türkçe kurallarla, harf duyarsız). */
  q: string;
  /** Seçilenlerden herhangi biri; boş = hepsi. Sıra sabit (TASK_STATUSES sırası). */
  status: TaskStatus[];
  priority: TaskPriority[];
  assignee: AssigneeFilter;
  /** `yyyy-MM-dd`, dahil; boş = sınır yok */
  dueFrom: string;
  dueTo: string;
  overdue: boolean;
}

export const EMPTY_FILTERS: TaskFilters = {
  q: '',
  status: [],
  priority: [],
  assignee: '',
  dueFrom: '',
  dueTo: '',
  overdue: false,
};

/**
 * Görev listesinin URL'deki hali, örn. `?q=rapor&status=TODO,IN_PROGRESS&assignee=me&overdue=1&sort=dueDate,asc&page=2`
 * (sayfa 1 tabanlı; varsayılanlar ve boş filtreler yazılmaz). URL tek doğruluk kaynağıdır: kontroller URL'yi
 * değiştirir, liste URL'den okunur. Arayüz tek sütuna göre sıralar (sunucu birden fazlasını destekler).
 * `assignee=me` URL'de "me" olarak kalır: paylaşılan bağlantıyı açan kişi kendi görevlerini görür.
 */
export interface TaskListQuery {
  filters: TaskFilters;
  sort: TaskSort;
  /** 0 tabanlı */
  page: number;
}

export const DEFAULT_QUERY: TaskListQuery = { filters: EMPTY_FILTERS, sort: DEFAULT_SORT, page: 0 };

/** Bozuk/elle yazılmış değerler sessizce atlanır veya düzeltilir (sunucuya geçersiz istek gitmez). */
export function toTaskListQuery(params: ParamMap): TaskListQuery {
  let dueFrom = parseDate(params.get('dueFrom'));
  let dueTo = parseDate(params.get('dueTo'));
  if (dueFrom && dueTo && dueFrom > dueTo) {
    [dueFrom, dueTo] = [dueTo, dueFrom];
  }
  return {
    filters: {
      q: (params.get('q') ?? '').trim().slice(0, TASK_TITLE_MAX),
      status: parseList(params.get('status'), TASK_STATUSES),
      priority: parseList(params.get('priority'), TASK_PRIORITIES),
      assignee: parseAssignee(params.get('assignee')),
      dueFrom,
      dueTo,
      overdue: params.get('overdue') === '1' || params.get('overdue') === 'true',
    },
    sort: parseSort(params.get('sort')),
    page: parsePage(params.get('page')),
  };
}

/** `router.navigate` için: boş/varsayılan değerler `null` (URL'den silinir). */
export function toQueryParams(query: TaskListQuery): Params {
  const f = query.filters;
  return {
    q: f.q || null,
    status: f.status.length ? f.status.join(',') : null,
    priority: f.priority.length ? f.priority.join(',') : null,
    assignee: f.assignee || null,
    dueFrom: f.dueFrom || null,
    dueTo: f.dueTo || null,
    overdue: f.overdue ? 1 : null,
    sort: isDefaultSort(query.sort) ? null : `${query.sort.field},${query.sort.direction}`,
    page: query.page > 0 ? query.page + 1 : null,
  };
}

export function sameQuery(a: TaskListQuery, b: TaskListQuery): boolean {
  return JSON.stringify(toQueryParams(a)) === JSON.stringify(toQueryParams(b));
}

/**
 * API parametreleri. `assignee=me` için kullanıcı kimliği bilinmiyorsa `null`: istek gönderilmez, sonuç boş
 * sayılır ("bana atanan" atanmamışları ya da herkesi göstermesin).
 */
export function toApiParams(query: TaskListQuery, projectId: number, currentUserId: number | null): TaskListParams | null {
  const f = query.filters;
  let assigneeId: number | 'none' | undefined;
  if (f.assignee === 'me') {
    if (currentUserId === null) {
      return null;
    }
    assigneeId = currentUserId;
  } else if (f.assignee === 'none') {
    assigneeId = 'none';
  } else if (f.assignee) {
    assigneeId = Number(f.assignee);
  }
  const params: TaskListParams = { projectId, sort: [query.sort], page: query.page, size: TASK_PAGE_SIZE };
  // Yalnızca dolu filtreler (boş anahtar taşınmaz)
  if (f.q) params.q = f.q;
  if (f.status.length) params.status = f.status;
  if (f.priority.length) params.priority = f.priority;
  if (assigneeId !== undefined) params.assigneeId = assigneeId;
  if (f.dueFrom) params.dueFrom = f.dueFrom;
  if (f.dueTo) params.dueTo = f.dueTo;
  if (f.overdue) params.overdue = true;
  return params;
}

/** Açık filtre sayısı (arama dahil; tarih aralığı tek filtre sayılır). Mobil "Filtreler (n)" düğmesi için. */
export function activeFilterCount(filters: TaskFilters): number {
  return (
    (filters.q ? 1 : 0) +
    (filters.status.length ? 1 : 0) +
    (filters.priority.length ? 1 : 0) +
    (filters.assignee ? 1 : 0) +
    (filters.dueFrom || filters.dueTo ? 1 : 0) +
    (filters.overdue ? 1 : 0)
  );
}

/** Sütun başlığına tıklama: aynı sütunsa yön değişir, başka sütunsa o sütunun ilk yönü. */
export function nextSort(current: TaskSort, field: TaskSortField): TaskSort {
  if (current.field === field) {
    return { field, direction: current.direction === 'asc' ? 'desc' : 'asc' };
  }
  return { field, direction: FIRST_DIRECTION[field] };
}

/** Çoklu seçimde bir değeri açar/kapatır; sonuç her zaman `order` sırasında (URL kararlı kalsın). */
export function toggleValue<T>(selected: readonly T[], value: T, order: readonly T[]): T[] {
  const next = selected.includes(value) ? selected.filter((v) => v !== value) : [...selected, value];
  return order.filter((v) => next.includes(v));
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

/** Bilinmeyenler atılır, tekrarlar birleşir, sıra `allowed` sırası. */
function parseList<T extends string>(value: string | null, allowed: readonly T[]): T[] {
  const tokens = (value ?? '').split(',').map((t) => t.trim().toUpperCase());
  return allowed.filter((v) => tokens.includes(v));
}

function parseAssignee(value: string | null): AssigneeFilter {
  const v = (value ?? '').trim();
  if (v === 'me' || v === 'none') {
    return v;
  }
  return /^[1-9]\d{0,17}$/.test(v) ? (v as `${number}`) : '';
}

/** `yyyy-MM-dd` ve gerçek bir gün (2026-02-30 değil). */
function parseDate(value: string | null): string {
  const v = (value ?? '').trim();
  if (!DATE.test(v)) {
    return '';
  }
  const [y, m, d] = v.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  return date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d ? v : '';
}
