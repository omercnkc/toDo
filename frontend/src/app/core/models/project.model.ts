export type ProjectStatus = 'ACTIVE' | 'ARCHIVED';
export type MemberRole = 'OWNER' | 'MEMBER';

/** Backend sınırları (docs/06) — formlar aynı kuralı uygular. */
export const PROJECT_NAME_MAX = 150;
export const PROJECT_DESCRIPTION_MAX = 5000;

/** `GET /api/projects` ve `/api/projects/{id}` (liste ve detayda aynı biçim). */
export interface Project {
  id: number;
  name: string;
  description: string | null;
  status: ProjectStatus;
  ownerId: number;
  /** İsteği yapanın rolü; üye olmayan ADMIN için null. */
  myRole: MemberRole | null;
  /** Düzenle/arşivle/davet/üye çıkar düğmeleri gösterilsin mi (OWNER veya ADMIN). */
  canManage: boolean;
  memberCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectRequest {
  name: string;
  description: string | null;
}

/**
 * PUT tam güncellemedir: `description` gönderilmezse silinir. `status` boşsa değişmez;
 * arşivden geri almak için `ACTIVE`.
 */
export interface UpdateProjectRequest extends ProjectRequest {
  status?: ProjectStatus;
}

/** Liste filtresi: boş (`null`) → tüm durumlar. */
export interface ProjectQuery {
  q: string;
  status: ProjectStatus | null;
  page: number;
  size: number;
}
