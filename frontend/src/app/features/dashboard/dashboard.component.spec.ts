import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { NgChartsModule } from 'ng2-charts';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { DashboardSummary, ProjectProgress } from '../../core/models/dashboard.model';
import { Page } from '../../core/models/page.model';
import { localToday, Task, TaskListParams } from '../../core/models/task.model';
import { DashboardService } from '../../core/services/dashboard.service';
import { TaskService } from '../../core/services/task.service';
import { InvitationService } from '../../core/services/invitation.service';
import { SharedModule } from '../../shared/shared.module';
import { addDays } from './dashboard-lists';
import { DashboardComponent } from './dashboard.component';

function task(id: number, extra: Partial<Task> = {}): Task {
  return {
    id, projectId: 7, title: `Görev ${id}`, description: null, status: 'TODO', priority: 'MEDIUM', position: 0,
    assigneeId: 1, reporterId: 1, dueDate: null, canDelete: true, createdAt: '', updatedAt: '', ...extra,
  };
}

function page(content: Task[], totalElements = content.length): Page<Task> {
  return { content, page: 0, size: 5, totalElements, totalPages: 1 };
}

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
  let tasks: jasmine.SpyObj<TaskService>;
  /** Liste isteklerinin yanıtı: bana atanan mı (assigneeId var) yaklaşan mı. */
  let assignedPage: Page<Task>;
  let upcomingPage: Page<Task>;

  async function create(params: Record<string, string> = {}): Promise<void> {
    queryParams = new BehaviorSubject(convertToParamMap(params));
    const user = { id: 1, email: 'ayse@ornek.com', fullName: 'Ayşe Yılmaz', role };
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, SharedModule, NgChartsModule],
      declarations: [DashboardComponent],
      providers: [
        { provide: DashboardService, useValue: dashboard },
        { provide: TaskService, useValue: tasks },
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
    tasks = jasmine.createSpyObj<TaskService>('TaskService', ['list']);
    assignedPage = page([
      task(1, { title: 'Giriş ekranı', dueDate: '2000-01-01', status: 'IN_PROGRESS' }),
      task(2, { projectId: 99, title: 'Bilinmeyen projede' }),
    ], 6);
    upcomingPage = page([]);
    tasks.list.and.callFake((params: TaskListParams) => of(params.assigneeId !== undefined ? assignedPage : upcomingPage));
  });

  const listCard = (key: string) => el().querySelector<HTMLElement>(`[data-list="${key}"]`)!;

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

  // ---------- 6-E: kişisel listeler ----------

  it('listeler özetle birlikte doğru parametrelerle istenir', async () => {
    await create();

    const calls = tasks.list.calls.allArgs();
    expect(calls.length).toBe(2);
    const assigned = calls.find(([p]) => p.assigneeId !== undefined)!;
    const upcoming = calls.find(([p]) => p.assigneeId === undefined)!;
    expect(assigned[0]).toEqual({ assigneeId: 1, status: ['TODO', 'IN_PROGRESS'], sort: [{ field: 'dueDate', direction: 'asc' }], size: 5 });
    expect(assigned[1]).withContext('hata kartta gösterilir, genel bildirim yok').toBeTrue();
    expect(upcoming[0].dueFrom).toBe(localToday());
    expect(upcoming[0].dueTo).toBe(addDays(localToday(), 6));
    expect(upcoming[0].projectId).withContext('projeler arası').toBeUndefined();
  });

  it('bana atanan: başlık, proje adı, durum, gecikme; görev projesinin listesini aynı filtreyle açar', async () => {
    await create();
    const card = listCard('assigned');

    const items = card.querySelectorAll<HTMLAnchorElement>('li a');
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('Giriş ekranı');
    expect(items[0].textContent).toContain('Web Sitesi');
    expect(items[0].textContent).toContain('Devam Ediyor');
    expect(items[0].querySelector('.text-danger')!.textContent).toContain('gecikti');
    expect(items[0].getAttribute('href')).toBe('/projects/7/tasks?assignee=me&status=TODO,IN_PROGRESS&sort=dueDate,asc');
    expect(items[1].textContent).toContain('Proje #99');
    expect(items[1].textContent).toContain('Tarih yok');
    expect(card.querySelector('.list-count')!.textContent!.trim()).toBe('2 / 6');
  });

  it('boş liste açıklaması; yaklaşan bağlantısı tarih aralığıyla', async () => {
    upcomingPage = page([task(5, { dueDate: addDays(localToday(), 2) })]);
    assignedPage = page([]);
    await create();

    expect(listCard('assigned').textContent).toContain('Sana atanmış açık görev yok.');
    const link = listCard('upcoming').querySelector('li a')!.getAttribute('href')!;
    expect(link).toContain(`dueFrom=${localToday()}`);
    expect(link).toContain(`dueTo=${addDays(localToday(), 6)}`);
    expect(listCard('upcoming').textContent).toContain('Önümüzdeki 7 gün');
  });

  it('liste hatası yalnızca o kartı etkiler; tekrar dene hepsini yeniler', async () => {
    tasks.list.and.callFake((params: TaskListParams) =>
      params.assigneeId !== undefined ? throwError(() => new HttpErrorResponse({ status: 503 })) : of(upcomingPage),
    );
    await create();

    expect(listCard('assigned').textContent).toContain('Liste yüklenemedi');
    expect(listCard('upcoming').textContent).not.toContain('Liste yüklenemedi');
    expect(stat('open')).withContext('özet etkilenmez').toBe('5');

    tasks.list.and.callFake((params: TaskListParams) => of(params.assigneeId !== undefined ? assignedPage : upcomingPage));
    listCard('assigned').querySelector<HTMLButtonElement>('button')!.click();
    expect(listCard('assigned').querySelectorAll('li').length).toBe(2);
  });

  it('tüm sistem kapsamında kişisel listeler yok', async () => {
    role = 'ADMIN';
    await create({ scope: 'all' });

    expect(tasks.list).not.toHaveBeenCalled();
    expect(el().querySelector('[data-list]')).toBeNull();
  });

  it('proje satırındaki geciken sayısı gecikmiş filtresiyle listeye bağlanır', async () => {
    await create();
    const overdue = el().querySelector<HTMLAnchorElement>('.project-progress li a.text-danger')!;
    expect(overdue.textContent!.trim()).toBe('2 geciken');
    expect(overdue.getAttribute('href')).toBe('/projects/7/tasks?overdue=1&sort=dueDate,asc');
  });
});
