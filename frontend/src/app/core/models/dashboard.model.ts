/** `GET /api/dashboard/summary` kapsamı: üye olunan projeler veya tüm sistem (yalnızca ADMIN). */
export type DashboardScope = 'mine' | 'all';

/** Proje başına sayılar (docs/06 "dashboard-service"). */
export interface ProjectProgress {
  id: number;
  name: string;
  status: 'ACTIVE' | 'ARCHIVED';
  /** `all` kapsamında üye olunmayabilir: null. */
  myRole: 'OWNER' | 'MEMBER' | null;
  total: number;
  todo: number;
  inProgress: number;
  done: number;
  overdue: number;
  /** Tamamlanan / toplam, aşağı yuvarlanmış; görevsiz projede 0. */
  progressPercent: number;
}

/**
 * Dashboard özeti. Toplamlar arşivdeki projeleri de kapsar (görev listesiyle aynı sayılar).
 * Sunucuda kullanıcı başına 30 sn önbelleklenir: `generatedAt` hesaplama anıdır.
 */
export interface DashboardSummary {
  scope: DashboardScope;
  projectCount: number;
  activeProjectCount: number;
  archivedProjectCount: number;
  taskTotals: { todo: number; inProgress: number; done: number };
  totalTasks: number;
  openTasks: number;
  overdueCount: number;
  assignedToMe: { open: number; overdue: number };
  /** Aktifler önce, Türkçe ada göre; arşiv sonda. */
  projects: ProjectProgress[];
  generatedAt: string;
}
