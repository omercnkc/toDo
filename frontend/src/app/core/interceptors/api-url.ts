import { environment } from '../../../environments/environment';

/** Kendi backend'imize giden istek mi (relative `/api/...`)? Token başka adreslere gönderilmez. */
export function isApiUrl(url: string): boolean {
  return url.startsWith(`${environment.apiUrl}/`);
}

/** Kimlik uçları (login, register, refresh, logout): Bearer gerekmez, 401'de yenileme denenmez. */
export function isAuthUrl(url: string): boolean {
  return url.startsWith(`${environment.apiUrl}/auth/`);
}
