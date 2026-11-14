import { User } from './user.model';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

/**
 * Giriş/yenileme yanıtı. Refresh token gövdede değil httpOnly cookie'dedir;
 * JavaScript onu hiç görmez (docs/07).
 */
export interface AuthResponse {
  accessToken: string;
  /** Access token ömrü (saniye). */
  expiresIn: number;
  user: User;
}

/**
 * Login sayfasına router state ile taşınan bilgi (URL'de/geçmişte görünmesin diye query param değil).
 * - `registered`: kayıttan gelindi, e-posta doldurulur.
 * - `passwordChanged`: şifre değişti, tüm oturumlar kapandı; yeniden giriş istenir.
 */
export interface LoginNavigationState {
  email?: string;
  notice?: 'registered' | 'passwordChanged';
}
