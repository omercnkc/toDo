import { fakeAsync, tick } from '@angular/core/testing';

import { Toast, ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;
  let current: Toast[];

  beforeEach(() => {
    service = new ToastService();
    service.toasts$.subscribe((toasts) => (current = toasts));
  });

  it('bildirimi ekler ve süre dolunca kaldırır', fakeAsync(() => {
    service.success('Kaydedildi');
    expect(current.map((t) => [t.message, t.type])).toEqual([['Kaydedildi', 'success']]);

    tick(ToastService.DEFAULT_DURATION_MS);
    expect(current).toEqual([]);
  }));

  it('aynı mesaj üst üste eklenmez', fakeAsync(() => {
    service.error('Sunucuya ulaşılamıyor');
    service.error('Sunucuya ulaşılamıyor');
    service.error('Başka hata');

    expect(current.length).toBe(2);
    tick(ToastService.DEFAULT_DURATION_MS);
  }));

  it('elle kapatılabilir', fakeAsync(() => {
    service.show('Bilgi', 'info', 0);
    service.dismiss(current[0].id);

    expect(current).toEqual([]);
  }));
});
