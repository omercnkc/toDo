import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { NgChartsModule } from 'ng2-charts';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { DashboardSummary, ProjectProgress } from '../../core/models/dashboard.model';
import { DashboardService } from '../../core/services/dashboard.service';
import { InvitationService } from '../../core/services/invitation.service';
import { SharedModule } from '../../shared/shared.module';
import { DashboardComponent } from './dashboard.component';

function project(id: number, extra: Partial<ProjectProgress> = {}): ProjectProgress {
  return {
    id, name: `Proje ${id}`, status: 'ACTIVE', myRole: 'MEMBER', total: 4, todo: 1, inProgress: 1, done: 2,
    overdue: 0, progressPercent: 50, ...extra,
  };
}

function summary(extra: Partial<DashboardSummary> = {}): DashboardSummary {
  return {
    scope: 'mine', projectCount: 3, activeProjectCount: 2, archivedProjectCount: 1,
    taskTotals: { todo: 3, inProgress: 2, done: 5 }, totalTasks: 10, openTasks: 5, overdueCount: 2,
    assignedToMe: { open: 4, overdue: 1 },
    projects: [
      project(7, { name: 'Web Sitesi', myRole: 'OWNER', total: 6, done: 3, overdue: 2, progressPercent: 50 }),
      project(8, { name: 'Boş', total: 0, todo: 0, inProgress: 0, done: 0, progressPercent: 0 }),
      project(9, { name: 'Eski', status: 'ARCHIVED', total: 4, done: 2 }),
    ],
    generatedAt: '2026-11-17T10:30:00Z',
    ...extra,
  };
}

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let dashboard: jasmine.SpyObj<DashboardService>;
  let router: Router;
  let queryParams: BehaviorSubject<ParamMap>;
  let role: 'USER' | 'ADMIN';
  let pending: BehaviorSubject<number>;

  async function create(params: Record<string, string> = {}): Promise<void> {
    queryParams = new BehaviorSubject(convertToParamMap(params));
    const user = { id: 1, email: 'ayse@ornek.com', fullName: 'Ayşe Yılmaz', role };
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, SharedModule, NgChartsModule],
      declarations: [DashboardComponent],
      providers: [
        { provide: DashboardService, useValue: dashboard },
        { provide: AuthService, useValue: { currentUser: user, currentUser$: of(user) } },
        { provide: InvitationService, useValue: { pendingCount$: pending } },
        { provide: ActivatedRoute, useValue: { queryParamMap: queryParams } },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function el(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const stat = (name: string) => el().querySelector(`[data-stat="${name}"]`)?.textContent?.trim();

  beforeEach(() => {
    dashboard = jasmine.createSpyObj<DashboardService>('DashboardService', ['summary']);
    dashboard.summary.and.returnValue(of(summary()));
    role = 'USER';
    pending = new BehaviorSubject(0);
  });

  it('özet kartları: aktif proje (+arşiv), açık/toplam, geciken, bana atanan', async () => {
    await create();

    expect(dashboard.summary).toHaveBeenCalledOnceWith('mine');
    expect(stat('projects')).toBe('2');
    expect(el().textContent).toContain('+1 arşivde');
    expect(stat('open')).toBe('5');
    expect(el().textContent).toContain('toplam 10 görevin');
    expect(stat('overdue')).toBe('2');
    expect(el().querySelector('[data-stat="overdue"]')!.closest('.stat-card')!.classList).toContain('stat-alert');
    expect(stat('mine')).toBe('4');
    expect(el().textContent).toContain('1 tanesi gecikmiş');
  });

  it('durum dağılımı: grafik verisi, açıklamada sayı ve yüzde, tuvalin metin karşılığı', async () => {
    await create();

    expect(component.chartData.datasets[0].data).toEqual([3, 2, 5]);
    expect(component.chartData.labels).toEqual(['Yapılacak', 'Devam Ediyor', 'Tamamlandı']);
    const legend = Array.from(el().querySelectorAll('.status-legend li')).map((li) =>
      Array.from(li.querySelectorAll('span')).map((span) => span.textContent!.trim()).filter(Boolean).join(' '),
    );
    expect(legend).toEqual(['Yapılacak 3 %30', 'Devam Ediyor 2 %20', 'Tamamlandı 5 %50']);
    const canvas = el().querySelector('canvas')!;
    expect(canvas.getAttribute('role')).toBe('img');
    expect(canvas.getAttribute('aria-label')).toBe('Görev durum dağılımı: Yapılacak 3, Devam Ediyor 2, Tamamlandı 5');
    expect(el().querySelector('.chart-total')!.textContent!.trim()).toBe('10');
  });

  it('proje ilerlemesi: listeye bağlantı, yüzde, rol, arşiv, geciken, görevsiz', async () => {
    await create();

    const rows = el().querySelectorAll<HTMLElement>('.project-progress li');
    expect(rows.length).toBe(3);
    const web = rows[0];
    expect(web.querySelector('a')!.getAttribute('href')).toBe('/projects/7/tasks');
    expect(web.textContent).toContain('3/6');
    expect(web.textContent).toContain('%50');
    expect(web.textContent).toContain('Sahibi');
    expect(web.textContent).toContain('2 geciken');
    expect(web.querySelector('.progress')!.getAttribute('aria-valuenow')).toBe('50');
    expect((web.querySelector('.progress-bar') as HTMLElement).style.width).toBe('50%');
    expect(rows[1].textContent).toContain('Görev yok');
    expect(rows[2].textContent).toContain('4 görev');
    expect(rows[2].classList).toContain('archived');
    expect(rows[2].textContent).toContain('Arşivde');
  });

  it('en fazla 8 proje; fazlası için "Tüm projeler" bağlantısı', async () => {
    dashboard.summary.and.returnValue(of(summary({ projects: Array.from({ length: 11 }, (_, i) => project(i + 1)) })));
    await create();

    expect(el().querySelectorAll('.project-progress li').length).toBe(8);
    const all = Array.from(el().querySelectorAll('a')).find((a) => a.textContent!.includes('Tüm projeler'))!;
    expect(all.textContent!.trim()).toBe('Tüm projeler (+3)');
    expect(all.getAttribute('href')).toBe('/projects?status=ALL');
  });

  it('USER: kapsam seçimi yok, URL\'deki scope=all yok sayılır', async () => {
    await create({ scope: 'all' });

    expect(dashboard.summary).toHaveBeenCalledOnceWith('mine');
    expect(el().querySelector('[aria-label="Kapsam"]')).toBeNull();
  });

  it('ADMIN: kapsam seçimi URL\'yi değiştirir; URL\'deki kapsam isteğe yansır', async () => {
    role = 'ADMIN';
    await create();
    const links = el().querySelectorAll<HTMLAnchorElement>('[aria-label="Kapsam"] a');
    expect(links[0].getAttribute('aria-current')).toBe('page');

    links[1].click();
    expect((router.navigate as jasmine.Spy).calls.mostRecent().args[1].queryParams).toEqual({ scope: 'all' });

    dashboard.summary.and.returnValue(of(summary({ scope: 'all' })));
    queryParams.next(convertToParamMap({ scope: 'all' }));
    expect(dashboard.summary).toHaveBeenCalledWith('all');
    expect(el().textContent).toContain('Tüm sistemin özeti');
    expect(el().querySelectorAll('[aria-label="Kapsam"] a')[1].getAttribute('aria-current')).toBe('page');

    el().querySelectorAll<HTMLAnchorElement>('[aria-label="Kapsam"] a')[0].click();
    expect((router.navigate as jasmine.Spy).calls.mostRecent().args[1].queryParams).toEqual({ scope: null });
  });

  it('hiç proje yoksa ilk kullanım kartı', async () => {
    dashboard.summary.and.returnValue(of(summary({ projectCount: 0, activeProjectCount: 0, archivedProjectCount: 0, projects: [], totalTasks: 0 })));
    await create();

    expect(el().textContent).toContain('Henüz bir projen yok');
    expect(el().querySelector('[data-stat]')).toBeNull();
  });

  it('proje var görev yok: grafik yerine açıklama', async () => {
    dashboard.summary.and.returnValue(of(summary({ taskTotals: { todo: 0, inProgress: 0, done: 0 }, totalTasks: 0, openTasks: 0, overdueCount: 0 })));
    await create();

    expect(el().querySelector('canvas')).toBeNull();
    expect(el().textContent).toContain('Henüz görev yok');
    expect(el().querySelector('[data-stat="overdue"]')!.closest('.stat-card')!.classList).not.toContain('stat-alert');
  });

  it('servise ulaşılamazsa açıklama ve tekrar dene', async () => {
    dashboard.summary.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    await create();
    expect(el().textContent).toContain('Özet şu an alınamıyor');

    dashboard.summary.and.returnValue(of(summary()));
    Array.from(el().querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes('Tekrar dene'))!.click();

    expect(dashboard.summary).toHaveBeenCalledTimes(2);
    expect(stat('open')).toBe('5');
  });

  it('403 ve diğer hatalar', async () => {
    dashboard.summary.and.returnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    await create();
    expect(el().textContent).toContain('yalnızca yöneticilere açık');
  });

  it('bekleyen davet uyarısı korunur', async () => {
    pending.next(2);
    await create();
    expect(el().textContent).toContain('2 bekleyen proje davetin var');
  });
});
