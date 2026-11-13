import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export type ToastType = 'success' | 'danger' | 'warning' | 'info';

export interface Toast {
  id: number;
  message: string;
  type: ToastType;
}

/** Ekranın köşesinde kısa süreli bildirimler (ToastContainerComponent gösterir). */
@Injectable({ providedIn: 'root' })
export class ToastService {
  static readonly DEFAULT_DURATION_MS = 5000;

  private readonly toastsSubject = new BehaviorSubject<Toast[]>([]);
  readonly toasts$: Observable<Toast[]> = this.toastsSubject.asObservable();
  private nextId = 1;

  show(message: string, type: ToastType = 'info', durationMs = ToastService.DEFAULT_DURATION_MS): void {
    // Aynı anda başarısız olan birkaç istek aynı mesajı üst üste yığmasın
    if (this.toastsSubject.value.some((t) => t.message === message && t.type === type)) {
      return;
    }
    const toast: Toast = { id: this.nextId++, message, type };
    this.toastsSubject.next([...this.toastsSubject.value, toast]);
    if (durationMs > 0) {
      setTimeout(() => this.dismiss(toast.id), durationMs);
    }
  }

  success(message: string): void {
    this.show(message, 'success');
  }

  error(message: string): void {
    this.show(message, 'danger');
  }

  dismiss(id: number): void {
    this.toastsSubject.next(this.toastsSubject.value.filter((t) => t.id !== id));
  }
}
