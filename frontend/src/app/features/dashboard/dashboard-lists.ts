import { Params } from '@angular/router';

import { TaskListParams } from '../../core/models/task.model';

/** Panel listelerinde en fazla görev. */
export const LIST_SIZE = 5;
/** "Yaklaşan": bugünden itibaren bu kadar gün (bugün dahil). */
export const UPCOMING_DAYS = 7;

/**
 * Panel listeleri ve görev listesine bağlantıları aynı tanımdan üretilir: bağlantıyı açan kişi panelde gördüğü
 * görevleri aynı sırayla listenin başında görür.
 */
export interface DashboardList {
  /** Projeler arası `GET /api/tasks` parametreleri (5-B). */
  api: TaskListParams;
  /** Bir görevin projesindeki görev listesine (`/projects/:id/tasks`) eklenecek URL filtreleri (5-D biçimi). */
  link: Params;
}

/** Bana atanan açık görevler, bitiş tarihi en yakın önce (tarihsizler sonda). */
export function assignedToMeList(userId: number): DashboardList {
  return {
    api: {
      assigneeId: userId,
      status: ['TODO', 'IN_PROGRESS'],
      sort: [{ field: 'dueDate', direction: 'asc' }],
      size: LIST_SIZE,
    },
    link: { assignee: 'me', status: 'TODO,IN_PROGRESS', sort: 'dueDate,asc' },
  };
}

/** Önümüzdeki {@link UPCOMING_DAYS} günde bitecek açık görevler (gecikmişler değil: onlar "Geciken" kartında). */
export function upcomingList(today: string): DashboardList {
  const until = addDays(today, UPCOMING_DAYS - 1);
  return {
    api: {
      status: ['TODO', 'IN_PROGRESS'],
      dueFrom: today,
      dueTo: until,
      sort: [{ field: 'dueDate', direction: 'asc' }],
      size: LIST_SIZE,
    },
    link: { status: 'TODO,IN_PROGRESS', dueFrom: today, dueTo: until, sort: 'dueDate,asc' },
  };
}

/** `yyyy-MM-dd` + n gün (takvim günü; saat dilimi kaymasın diye UTC üzerinden). */
export function addDays(date: string, days: number): string {
  const [y, m, d] = date.split('-').map(Number);
  const result = new Date(Date.UTC(y, m - 1, d + days));
  return result.toISOString().slice(0, 10);
}
