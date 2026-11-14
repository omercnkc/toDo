import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * Ad baş harflerinden yuvarlak avatar (fotoğraf yok). Ekran okuyuculardan gizlidir; ad yanında zaten yazılır.
 * `<app-avatar [name]="user.fullName" size="lg"></app-avatar>`; koyu zeminde (navbar) `variant="onDark"`.
 */
@Component({
  selector: 'app-avatar',
  template: `<span class="avatar" [ngClass]="['avatar-' + size, 'avatar-' + variant]" aria-hidden="true">{{
    initials
  }}</span>`,
  styles: [
    `
      :host {
        display: inline-flex;
        flex-shrink: 0;
      }
      .avatar {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: 50%;
        font-weight: 600;
        line-height: 1;
        user-select: none;
      }
      .avatar-sm { width: 2rem; height: 2rem; font-size: 0.8rem; }
      .avatar-md { width: 2.5rem; height: 2.5rem; font-size: 0.95rem; }
      .avatar-lg { width: 5rem; height: 5rem; font-size: 1.75rem; }
      .avatar-default { background-color: rgba(79, 70, 229, 0.12); color: #4f46e5; }
      .avatar-onDark { background-color: rgba(255, 255, 255, 0.2); color: #fff; }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AvatarComponent {
  @Input() name = '';
  @Input() size: 'sm' | 'md' | 'lg' = 'md';
  @Input() variant: 'default' | 'onDark' = 'default';

  get initials(): string {
    return initialsOf(this.name);
  }
}

/** "Ayşe Nur Yılmaz" → "AY" (ilk ve son kelimenin baş harfi, Türkçe büyük harf). */
export function initialsOf(fullName: string): string {
  const words = (fullName ?? '').trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return '?';
  }
  const first = words[0].charAt(0);
  const last = words.length > 1 ? words[words.length - 1].charAt(0) : '';
  return (first + last).toLocaleUpperCase('tr-TR');
}
