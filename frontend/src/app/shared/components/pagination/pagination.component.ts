import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * Sayfa düğmeleri: Önceki · 1 … 4 5 6 … 10 · Sonraki. Tek sayfa varsa hiç görünmez.
 * Sayfa numaraları 0 tabanlıdır (backend gibi); ekranda 1 tabanlı yazılır.
 *
 * `<app-pagination [page]="query.page" [totalPages]="result.totalPages" (pageChange)="goToPage($event)">`
 */
@Component({
  selector: 'app-pagination',
  template: `
    <nav *ngIf="totalPages > 1" [attr.aria-label]="label">
      <ul class="pagination justify-content-center flex-wrap mb-0">
        <li class="page-item" [class.disabled]="page === 0">
          <button type="button" class="page-link" (click)="go(page - 1)" [disabled]="page === 0">Önceki</button>
        </li>
        <li *ngFor="let p of pages" class="page-item" [class.active]="p === page" [class.disabled]="p === null">
          <span *ngIf="p === null" class="page-link">…</span>
          <button
            *ngIf="p !== null"
            type="button"
            class="page-link"
            (click)="go(p)"
            [attr.aria-current]="p === page ? 'page' : null"
            [attr.aria-label]="'Sayfa ' + (p + 1)"
          >
            {{ p + 1 }}
          </button>
        </li>
        <li class="page-item" [class.disabled]="page >= totalPages - 1">
          <button type="button" class="page-link" (click)="go(page + 1)" [disabled]="page >= totalPages - 1">
            Sonraki
          </button>
        </li>
      </ul>
    </nav>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaginationComponent {
  /** 0 tabanlı geçerli sayfa. */
  @Input() page = 0;
  @Input() totalPages = 0;
  @Input() label = 'Sayfalar';
  /** 0 tabanlı hedef sayfa; yalnızca geçerli ve farklı bir sayfa için yayılır. */
  @Output() readonly pageChange = new EventEmitter<number>();

  /** İlk, son ve geçerli sayfanın çevresi; aralar `null` (…). */
  get pages(): (number | null)[] {
    return pageWindow(this.page, this.totalPages);
  }

  go(target: number): void {
    if (target >= 0 && target < this.totalPages && target !== this.page) {
      this.pageChange.emit(target);
    }
  }
}

export function pageWindow(current: number, total: number): (number | null)[] {
  const pages: (number | null)[] = [];
  for (let i = 0; i < total; i++) {
    if (i === 0 || i === total - 1 || Math.abs(i - current) <= 1) {
      pages.push(i);
    } else if (pages[pages.length - 1] !== null) {
      pages.push(null);
    }
  }
  return pages;
}
