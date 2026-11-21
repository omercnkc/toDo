import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StatePanelComponent } from './state-panel.component';

@Component({
  template: `
    <app-state-panel [heading]="heading" [message]="message" [level]="level" [tone]="tone">
      <button *ngIf="withAction" type="button">Tekrar dene</button>
    </app-state-panel>
  `,
})
class HostComponent {
  heading = 'Proje yüklenemedi';
  message = 'Bağlantını kontrol edip tekrar dene.';
  level: 1 | 2 = 1;
  tone: 'error' | 'empty' = 'error';
  withAction = true;
}

describe('StatePanelComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ declarations: [StatePanelComponent, HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('hata: başlık seviyesi, açıklama, düğme ve ekran okuyucuya duyuru', () => {
    const heading = el().querySelector('[role="heading"]')!;
    expect(heading.textContent).toContain('Proje yüklenemedi');
    expect(heading.getAttribute('aria-level')).toBe('1');
    expect(el().textContent).toContain('Bağlantını kontrol edip');
    expect(el().querySelector('button')?.textContent).toContain('Tekrar dene');
    expect(el().querySelector('[role="alert"]')).not.toBeNull();
  });

  it('boş durum sessizdir; düğme yoksa düğme alanı gizlenir', () => {
    fixture.componentInstance.tone = 'empty';
    fixture.componentInstance.withAction = false;
    fixture.componentInstance.message = '';
    fixture.detectChanges();

    expect(el().querySelector('[role="alert"]')).toBeNull();
    expect(el().querySelectorAll('p').length).toBe(1);
    expect(getComputedStyle(el().querySelector('.state-actions')!).display).toBe('none');
  });
});
