import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';

import { ToastContainerComponent } from './components/toast-container/toast-container.component';

/** Birden çok özellik modülünün kullandığı ortak bileşenler (docs/08 "shared"). */
@NgModule({
  declarations: [ToastContainerComponent],
  imports: [CommonModule],
  exports: [CommonModule, ToastContainerComponent],
})
export class SharedModule {}
