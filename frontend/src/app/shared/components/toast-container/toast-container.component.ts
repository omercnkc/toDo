import { ChangeDetectionStrategy, Component } from '@angular/core';

import { Toast, ToastService } from '../../../core/services/toast.service';

/** ToastService bildirimlerini ekranın sağ altında gösterir (uygulama kökünde bir kez). */
@Component({
  selector: 'app-toast-container',
  templateUrl: './toast-container.component.html',
  styleUrls: ['./toast-container.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastContainerComponent {
  readonly toasts$ = this.toastService.toasts$;

  constructor(private readonly toastService: ToastService) {}

  dismiss(id: number): void {
    this.toastService.dismiss(id);
  }

  trackById(_: number, toast: Toast): number {
    return toast.id;
  }
}
