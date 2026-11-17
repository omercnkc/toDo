import { A11yModule } from '@angular/cdk/a11y';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';

import { Page } from '../../../core/models/page.model';
import { Project, ProjectMember } from '../../../core/models/project.model';
import { Task, TaskListParams } from '../../../core/models/task.model';
import { ProjectService } from '../../../core/services/project.service';
import { TaskService } from '../../../core/services/task.service';
import { ToastService } from '../../../core/services/toast.service';
import { SharedModule } from '../../../shared/shared.module';
import { TaskModalComponent } from '../task-modal/task-modal.component';
import { TaskViewSwitchComponent } from '../view-switch/task-view-switch.component';
import { TaskListComponent } from './task-list.component';

const PROJECT: Project = {
  id: 7, name: 'Web Sitesi', description: null, status: 'ACTIVE', ownerId: 1, myRole: 'OWNER',
  canManage: true, memberCount: 2, createdAt: '2026-11-01T10:00:00Z', updatedAt: '2026-11-01T10:00:00Z',
};
const MEMBERS: ProjectMember[] = [
  { userId: 1, email: 'sahip@ornek.com', fullName: 'Ayşe Yılmaz', enabled: true, role: 'OWNER', joinedAt: '' },
];

function task(id: number, extra: Partial<Task> = {}): Task {
  return {
    id, projectId: 7, title: `Görev ${id}`, description: null, status: 'TODO', priority: 'MEDIUM', position: 0,
    assigneeId: null, reporterId: 1, dueDate: null, canDelete: true, createdAt: '2026-11-10T10:00:00Z',
    updatedAt: '', ...extra,
  };
}

function page(content: Task[], pageIndex = 0, totalElements = content.length, size = 20): Page<Task> {
  return { content, page: pageIndex, size, totalElements, totalPages: Math.ceil(totalElements / size) };
}

describe('TaskListComponent', () => {
  let fixture: ComponentFixture<TaskListComponent>;
  let component: TaskListComponent;
  let tasks: jasmine.SpyObj<TaskService>;
  let projects: jasmine.SpyObj<ProjectService>;
  let router: Router;
  let queryParams: BehaviorSubject<ParamMap>;

  async function create(params: Record<string, string> = {}): Promise<void> {
    queryParams = new BehaviorSubject(convertToParamMap(params));
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, SharedModule, ReactiveFormsModule, A11yModule],
      declarations: [TaskListComponent, TaskModalComponent, TaskViewSwitchComponent],
      providers: [
        { provide: TaskService, useValue: tasks },
        { provide: ProjectService, useValue: projects },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error']) },
        {
          provide: ActivatedRoute,
          useValue: { parent: { paramMap: of(convertToParamMap({ id: '7' })) }, queryParamMap: queryParams },
        },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(TaskListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function el(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function lastListCall(): TaskListParams {
    return tasks.list.calls.mostRecent().args[0];
  }

  function navigatedParams(): Record<string, unknown> {
    return (router.navigate as jasmine.Spy).calls.mostRecent().args[1].queryParams;
  }

  beforeEach(() => {
    tasks = jasmine.createSpyObj<TaskService>('TaskService', ['list', 'create', 'update', 'changeStatus', 'delete']);
    projects = jasmine.createSpyObj<ProjectService>('ProjectService', ['get', 'members']);
    projects.get.and.returnValue(of(PROJECT));
    projects.members.and.returnValue(of(MEMBERS));
    tasks.list.and.returnValue(
      of(
        page([
          task(1, { title: 'Giriş ekranı', priority: 'HIGH', status: 'IN_PROGRESS', assigneeId: 1, dueDate: '2000-01-01' }),
          task(2, { assigneeId: 99, status: 'DONE', dueDate: '2000-01-01' }),
          task(3),
        ]),
      ),
    );
  });

  it('projenin görevlerini varsayılan sırayla ister ve tabloda gösterir', async () => {
    await create();

    expect(lastListCall()).toEqual({ projectId: 7, sort: [{ field: 'createdAt', direction: 'desc' }], page: 0, size: 20 });
    expect(tasks.list.calls.mostRecent().args[1]).toBeTrue();
    const rows = el().querySelectorAll('tbody tr');
    expect(rows.length).toBe(3);
    const first = rows[0].textContent!;
    expect(first).toContain('Giriş ekranı');
    expect(first).toContain('Devam Ediyor');
    expect(first).toContain('Yüksek');
    expect(first).toContain('Ayşe Yılmaz');
    expect(first).toContain('gecikti');
    expect(rows[1].textContent).toContain('Kullanıcı #99');
    expect(rows[1].textContent).withContext('tamamlanan görev gecikmiş sayılmaz').not.toContain('gecikti');
    expect(rows[2].textContent).toContain('Atanmamış');
    expect(el().querySelector('th[aria-sort="descending"]')?.textContent).toContain('Oluşturulma');
    expect(el().textContent).toContain('1–3 / 3');
  });

  it('URL\'deki sıralama ve sayfa isteğe yansır', async () => {
    tasks.list.and.returnValue(of(page([task(41)], 2, 41)));
    await create({ sort: 'dueDate,asc', page: '3' });

    expect(lastListCall().sort).toEqual([{ field: 'dueDate', direction: 'asc' }]);
    expect(lastListCall().page).toBe(2);
    expect(el().querySelector('th[aria-sort="ascending"]')?.textContent).toContain('Bitiş');
    expect(el().textContent).toContain('41–41 / 41');
  });

  it('sütun başlığı sıralamayı URL\'ye yazar: yeni sütunda ilk yön, aynı sütunda ters yön; sayfa sıfırlanır', async () => {
    await create({ page: '2' });
    const header = (label: string) =>
      Array.from(el().querySelectorAll<HTMLButtonElement>('th .sort-button')).find((b) => b.textContent!.includes(label))!;

    header('Öncelik').click();
    expect(navigatedParams()).toEqual({ sort: 'priority,desc', page: null });

    queryParams.next(convertToParamMap({ sort: 'priority,desc' }));
    header('Öncelik').click();
    expect(navigatedParams()).toEqual({ sort: 'priority,asc', page: null });

    header('Oluşturulma').click();
    expect(navigatedParams()).withContext('varsayılana (en yeni) dönünce URL temizlenir').toEqual({ sort: null, page: null });
  });

  it('URL değişince yeniden ister, aynı sorgu için tekrar istemez', async () => {
    await create();
    expect(tasks.list).toHaveBeenCalledTimes(1);

    queryParams.next(convertToParamMap({ page: '2' }));
    expect(tasks.list).toHaveBeenCalledTimes(2);
    expect(lastListCall().page).toBe(1);
    expect(projects.get).withContext('proje bilgisi bir kez yüklenir').toHaveBeenCalledTimes(1);

    queryParams.next(convertToParamMap({ page: '2', baska: 'x' }));
    expect(tasks.list).toHaveBeenCalledTimes(2);
  });

  it('sayfalama: 1 tabanlı sayfa URL\'ye yazılır', async () => {
    tasks.list.and.returnValue(of(page([task(1)], 0, 45)));
    await create();

    const next = Array.from(el().querySelectorAll<HTMLButtonElement>('app-pagination button')).find(
      (b) => b.textContent!.trim() === 'Sonraki',
    )!;
    next.click();

    expect(navigatedParams()).toEqual({ sort: null, page: 2 });
  });

  it('boş kalan sayfadan son sayfaya geçer (replace değil, yeni sorgu)', async () => {
    tasks.list.and.returnValue(of(page([], 3, 41)));
    await create({ page: '4' });

    expect(navigatedParams()).toEqual({ sort: null, page: 3 });
  });

  it('mobil sıralama seçimi', async () => {
    await create();
    const select = el().querySelector<HTMLSelectElement>('#task-sort')!;
    expect(component.sortOptions[Number(select.value)].label).toBe('En yeni');

    component.selectSort(String(component.sortOptions.findIndex((o) => o.label === 'Bitiş tarihi (yakın)')));

    expect(navigatedParams()).toEqual({ sort: 'dueDate,asc', page: null });
  });

  it('satıra tıklama ve Enter modalı açar; değişiklikten sonra aynı sayfa yeniden yüklenir', async () => {
    await create();
    const row = el().querySelector<HTMLElement>('tbody tr[data-task-id="2"]')!;

    row.click();
    expect(component.modalTask?.id).toBe(2);
    expect(el().querySelector('app-task-modal')).not.toBeNull();
    component.closeModal();

    row.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(component.modalTask?.id).toBe(2);

    component.onTaskChanged();
    expect(tasks.list).toHaveBeenCalledTimes(2);
    expect(lastListCall().page).toBe(0);
  });

  it('boş proje: ilk görev çağrısı; arşivde yeni görev düğmesi yok', async () => {
    tasks.list.and.returnValue(of(page([])));
    projects.get.and.returnValue(of({ ...PROJECT, status: 'ARCHIVED' }));
    await create();

    expect(el().textContent).toContain('Henüz görev yok');
    expect(el().textContent).toContain('arşivde');
    expect(Array.from(el().querySelectorAll('button')).some((b) => b.textContent!.includes('Yeni görev'))).toBeFalse();
  });

  it('erişim yoksa ve proje yoksa sayfada açıklanır', async () => {
    tasks.list.and.returnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    await create();
    expect(el().textContent).toContain('Bu projeye erişimin yok');
    expect(el().querySelector('table')).toBeNull();
  });

  it('proje bulunamadı', async () => {
    projects.get.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    await create();
    expect(el().textContent).toContain('Proje bulunamadı');
    expect(tasks.list).not.toHaveBeenCalled();
  });

  it('bağlantı hatasında tekrar dene', async () => {
    tasks.list.and.returnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    await create();
    expect(el().textContent).toContain('Görevler yüklenemedi');

    tasks.list.and.returnValue(of(page([task(5)])));
    Array.from(el().querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes('Tekrar dene'))!.click();

    expect(el().querySelectorAll('tbody tr').length).toBe(1);
  });

  it('yeni sayfa yüklenirken eski sonuç ekranda kalır', async () => {
    await create();
    const pending = new Subject<Page<Task>>();
    tasks.list.and.returnValue(pending.asObservable());

    queryParams.next(convertToParamMap({ sort: 'title' }));

    expect(el().querySelectorAll('tbody tr').length).toBe(3);
    expect(el().querySelector('.is-loading')).not.toBeNull();
    pending.next(page([task(9)]));
    expect(el().querySelectorAll('tbody tr').length).toBe(1);
    expect(el().querySelector('.is-loading')).toBeNull();
  });

  it('görünüm geçişi Liste\'yi etkin gösterir', async () => {
    await create();
    const current = el().querySelector('app-task-view-switch [aria-current="page"]');
    expect(current?.textContent?.trim()).toBe('Liste');
    expect(current?.getAttribute('href')).toBe('/projects/7/tasks');
  });
});
