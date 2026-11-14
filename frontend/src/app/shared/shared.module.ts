import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';

import { AvatarComponent } from './components/avatar/avatar.component';
import { PaginationComponent } from './components/pagination/pagination.component';
import { FieldErrorComponent } from './components/field-error/field-error.component';
import { ToastContainerComponent } from './components/toast-container/toast-container.component';

/**
 * Birden çok özellik modülünün kullandığı ortak bileşenler (docs/08 "shared").
 *
 * ReactiveFormsModule burada DIŞA AKTARILMAZ: AppModule de bu modülü (toast için) import ettiğinden
 * form kütüphanesi ana pakete girerdi (~60 kB). Form kullanan lazy modüller onu kendisi import eder.
 */
@NgModule({
  declarations: [ToastContainerComponent, FieldErrorComponent, AvatarComponent, PaginationComponent],
  imports: [CommonModule],
  exports: [CommonModule, ToastContainerComponent, FieldErrorComponent, AvatarComponent, PaginationComponent],
})
export class SharedModule {}
