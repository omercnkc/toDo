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
