import { HttpErrorResponse } from '@angular/common/http';

/** Backend'in ortak hata gövdesi (docs/06-api-contracts.md). */
export interface ApiError {
  timestamp: string;
  status: number;
  /** Makine tarafından okunacak kod, örn. `INVALID_CREDENTIALS`, `VALIDATION_ERROR`. */
  error: string;
  message: string;
  path: string;
  fieldErrors?: FieldError[];
}

export interface FieldError {
  field: string;
  message: string;
}

/** HTTP hatasından ApiError gövdesini çıkarır; gövde bu biçimde değilse null. */
export function extractApiError(error: unknown): ApiError | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const body: unknown = error.error;
  if (body && typeof body === 'object' && 'error' in body && 'message' in body) {
    return body as ApiError;
  }
  return null;
}
