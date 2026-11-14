import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';

import { FieldErrorComponent } from './components/field-error/field-error.component';
import { ToastContainerComponent } from './components/toast-container/toast-container.component';

/**
 * Birden çok özellik modülünün kullandığı ortak bileşenler (docs/08 "shared").
 *
 * ReactiveFormsModule burada DIŞA AKTARILMAZ: AppModule de bu modülü (toast için) import ettiğinden
 * form kütüphanesi ana pakete girerdi (~60 kB). Form kullanan lazy modüller onu kendisi import eder.
 */
@NgModule({
  declarations: [ToastContainerComponent, FieldErrorComponent],
  imports: [CommonModule],
  exports: [CommonModule, ToastContainerComponent, FieldErrorComponent],
})
export class SharedModule {}
