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

/** `GET /api/projects/{id}/members`: önce OWNER. auth-service'e ulaşılamazsa ad/e-posta/enabled null. */
export interface ProjectMember {
  userId: number;
  email: string | null;
  fullName: string | null;
  enabled: boolean | null;
  role: MemberRole;
  joinedAt: string;
}

export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';

/** Davetteki kişi; auth-service'e ulaşılamazsa ad/e-posta null. */
export interface InvitationPerson {
  id: number;
  fullName: string | null;
  email: string | null;
}

export interface Invitation {
  id: number;
  status: InvitationStatus;
  createdAt: string;
  respondedAt: string | null;
  project: { id: number; name: string };
  inviter: InvitationPerson;
  invitee: InvitationPerson;
}

/** Adı bilinmeyen kişi için gösterim adı. */
export function displayName(person: { fullName: string | null }, id: number): string {
  return person.fullName ?? `Kullanıcı #${id}`;
}
