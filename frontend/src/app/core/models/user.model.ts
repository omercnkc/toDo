export type Role = 'USER' | 'ADMIN';

/** Giriş/yenileme yanıtındaki kullanıcı özeti. */
export interface User {
  id: number;
  email: string;
  fullName: string;
  role: Role;
}

/** `GET /api/users/me` ve ADMIN ekranlarındaki detay. */
export interface UserDetail extends User {
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * Kullanıcı araması (`GET /api/users?q=`) sonucu. USER yalnızca `{id, email, fullName}` görür
 * (yalnızca aktif kullanıcılar); ADMIN ayrıntılı yanıt alır, `enabled` de gelir.
 */
export interface UserSummary {
  id: number;
  email: string;
  fullName: string;
  enabled?: boolean;
}
