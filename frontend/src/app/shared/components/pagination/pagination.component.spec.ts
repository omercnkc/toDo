import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { pageWindow, PaginationComponent } from './pagination.component';

/** OnPush bileşen gerçek kullanımdaki gibi @Input bağlamasıyla test edilir (Angular 13'te setInput yok). */
@Component({
  template: `<app-pagination [page]="page" [totalPages]="total" (pageChange)="emitted.push($event)"></app-pagination>`,
})
class HostComponent {
  page = 0;
  total = 0;
  emitted: number[] = [];
}

describe('PaginationComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  function render(page: number, total: number): HTMLElement {
    host.page = page;
    host.total = total;
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function button(el: HTMLElement, text: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === text)!;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ declarations: [PaginationComponent, HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
  });

  it('pencere: ilk, son ve geçerlinin çevresi', () => {
    expect(pageWindow(4, 10)).toEqual([0, null, 3, 4, 5, null, 9]);
    expect(pageWindow(0, 3)).toEqual([0, 1, 2]);
    expect(pageWindow(0, 0)).toEqual([]);
  });

  it('tek sayfada görünmez', () => {
    expect(render(0, 1).querySelector('nav')).toBeNull();
  });

  it('geçerli sayfayı işaretler, sınırda önceki pasif', () => {
    const el = render(0, 3);

    expect(el.querySelector('[aria-current="page"]')?.textContent?.trim()).toBe('1');
    expect(button(el, 'Önceki').disabled).toBeTrue();
    expect(button(el, 'Sonraki').disabled).toBeFalse();
  });

  it('tıklanan sayfayı 0 tabanlı yayar, geçerli sayfaya tıklamak olay yaymaz', () => {
    const el = render(1, 3);

    button(el, '3').click();
    button(el, '2').click();
    button(el, 'Önceki').click();

    expect(host.emitted).toEqual([2, 0]);
  });
});
