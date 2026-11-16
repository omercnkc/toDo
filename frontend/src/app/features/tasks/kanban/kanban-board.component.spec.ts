import { DragDropModule } from '@angular/cdk/drag-drop';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';

import { Project, ProjectMember } from '../../../core/models/project.model';
import { Board, Task, TaskStatus } from '../../../core/models/task.model';
import { ProjectService } from '../../../core/services/project.service';
import { TaskService } from '../../../core/services/task.service';
import { ToastService } from '../../../core/services/toast.service';
import { SharedModule } from '../../../shared/shared.module';
import { KanbanBoardComponent } from './kanban-board.component';

const PROJECT: Project = {
  id: 7, name: 'Web Sitesi', description: null, status: 'ACTIVE', ownerId: 1, myRole: 'OWNER',
  canManage: true, memberCount: 2, createdAt: '2026-11-01T10:00:00Z', updatedAt: '2026-11-01T10:00:00Z',
};
const MEMBERS: ProjectMember[] = [
  { userId: 1, email: 'sahip@ornek.com', fullName: 'Ayşe Yılmaz', enabled: true, role: 'OWNER', joinedAt: '' },
];

function task(id: number, status: TaskStatus, position: number, extra: Partial<Task> = {}): Task {
  return {
    id, projectId: 7, title: `Görev ${id}`, description: null, status, priority: 'MEDIUM', position,
    assigneeId: null, reporterId: 1, dueDate: null, canDelete: true, createdAt: '', updatedAt: '', ...extra,
  };
}

/** TODO: 1 2 3 · IN_PROGRESS: 4 · DONE: — */
function seed(): Board {
  return {
    TODO: [
      task(1, 'TODO', 0, { title: 'Giriş ekranı', priority: 'HIGH', assigneeId: 1, dueDate: '2000-01-01' }),
      task(2, 'TODO', 1, { assigneeId: 99 }),
      task(3, 'TODO', 2),
    ],
    IN_PROGRESS: [task(4, 'IN_PROGRESS', 0)],
    DONE: [],
  };
}

/** CDK'nın `drop` olayının bileşenin kullandığı kısmı. */
function dropEvent(from: TaskStatus, previousIndex: number, to: TaskStatus, currentIndex: number): any {
  return { previousContainer: { data: from }, container: { data: to }, previousIndex, currentIndex };
}

function ids(board: Board | null, status: TaskStatus): number[] {
  return board![status].map((t) => t.id);
}

describe('KanbanBoardComponent', () => {
  let fixture: ComponentFixture<KanbanBoardComponent>;
  let component: KanbanBoardComponent;
  let tasks: jasmine.SpyObj<TaskService>;
  let projects: jasmine.SpyObj<ProjectService>;
  let toast: jasmine.SpyObj<ToastService>;
  /** Her `move` çağrısının yanıtı testin elinde. */
  let moveResponses: Subject<Task>[];

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, SharedModule, DragDropModule, ScrollingModule],
      declarations: [KanbanBoardComponent],
      providers: [
        { provide: TaskService, useValue: tasks },
        { provide: ProjectService, useValue: projects },
        { provide: ToastService, useValue: toast },
        { provide: ActivatedRoute, useValue: { parent: { paramMap: of(convertToParamMap({ id: '7' })) } } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(KanbanBoardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function el(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function column(status: TaskStatus): HTMLElement {
    return el().querySelector<HTMLElement>(`#kanban-${status}`)!;
  }

  beforeEach(() => {
    tasks = jasmine.createSpyObj<TaskService>('TaskService', ['board', 'move']);
    projects = jasmine.createSpyObj<ProjectService>('ProjectService', ['get', 'members']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
    projects.get.and.returnValue(of(PROJECT));
    projects.members.and.returnValue(of(MEMBERS));
    tasks.board.and.callFake(() => of(seed()));
    moveResponses = [];
    tasks.move.and.callFake(() => {
      const response = new Subject<Task>();
      moveResponses.push(response);
      return response.asObservable();
    });
  });

  it('üç kolonu, sayıları ve kart bilgilerini gösterir', async () => {
    await create();

    expect(tasks.board).toHaveBeenCalledWith(7, true);
    expect(projects.get).toHaveBeenCalledWith(7, true);
    expect(column('TODO').querySelectorAll('.kanban-card').length).toBe(3);
    expect(column('IN_PROGRESS').querySelectorAll('.kanban-card').length).toBe(1);
    expect(column('DONE').textContent).toContain('Görev yok');
    const titles = Array.from(el().querySelectorAll('h2')).map((h) => h.textContent?.replace(/\s+/g, ' ').trim());
    expect(titles).toEqual(['Yapılacak 3', 'Devam Ediyor 1', 'Tamamlandı 0']);

    const first = column('TODO').querySelector<HTMLElement>('[data-task-id="1"]')!;
    expect(first.textContent).toContain('Giriş ekranı');
    expect(first.textContent).toContain('Yüksek');
    expect(first.textContent).toContain('01.01.2000');
    expect(first.querySelector('.due')!.classList).toContain('text-danger'); // gecikmiş
    expect(first.textContent).toContain('Atanan: Ayşe Yılmaz');
    expect(first.querySelector('app-avatar')!.textContent).toContain('AY');
    // Üye listesinde olmayan atanan (çıkarılmış olabilir)
    expect(column('TODO').querySelector('[data-task-id="2"]')!.textContent).toContain('Kullanıcı #99');
  });

  it('üye adları alınamazsa pano yine açılır', async () => {
    projects.members.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    await create();

    expect(column('TODO').querySelector('[data-task-id="1"]')!.textContent).toContain('Kullanıcı #1');
  });

  it('sürükle-bırak iyimser: board hemen değişir, sonra taşıma gönderilir', async () => {
    await create();

    component.drop(dropEvent('TODO', 0, 'IN_PROGRESS', 1));

    // Yanıt gelmeden ekranda yeni yerinde
    expect(ids(component.board, 'TODO')).toEqual([2, 3]);
    expect(ids(component.board, 'IN_PROGRESS')).toEqual([4, 1]);
    expect(column('IN_PROGRESS').querySelectorAll('.kanban-card').length).toBe(2);
    expect(el().textContent).toContain('Kaydediliyor');
    expect(tasks.move).toHaveBeenCalledOnceWith(1, 'IN_PROGRESS', 1);

    moveResponses[0].next(task(1, 'IN_PROGRESS', 1));
    moveResponses[0].complete();

    expect(component.pendingMoves).toBe(0);
    expect(ids(component.board, 'IN_PROGRESS')).toEqual([4, 1]);
    expect(tasks.board).toHaveBeenCalledTimes(1); // sunucu aynı yere koydu: yeniden yükleme yok
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('aynı yere bırakma istek göndermez', async () => {
    await create();

    component.drop(dropEvent('TODO', 1, 'TODO', 1));

    expect(tasks.move).not.toHaveBeenCalled();
  });

  it('hata olursa eski hâline döner, mesaj gösterir ve panoyu sunucudan yeniler', async () => {
    await create();
    component.drop(dropEvent('TODO', 0, 'DONE', 0));
    expect(ids(component.board, 'DONE')).toEqual([1]);

    moveResponses[0].error(new HttpErrorResponse({
      status: 409, error: { error: 'PROJECT_ARCHIVED', message: 'Arşivlenmiş projenin görevleri değiştirilemez' },
    }));

    expect(ids(component.board, 'TODO')).toEqual([1, 2, 3]);
    expect(ids(component.board, 'DONE')).toEqual([]);
    expect(toast.error).toHaveBeenCalledWith('Arşivlenmiş projenin görevleri değiştirilemez');
    expect(tasks.board).toHaveBeenCalledTimes(2);
    expect(component.pendingMoves).toBe(0);
  });

  it('5xx hatasında bildirimi interceptor gösterir (çift bildirim yok), yine geri alınır', async () => {
    await create();
    component.drop(dropEvent('TODO', 2, 'TODO', 0));

    moveResponses[0].error(new HttpErrorResponse({ status: 503 }));

    expect(toast.error).not.toHaveBeenCalled();
    expect(ids(component.board, 'TODO')).toEqual([1, 2, 3]);
  });

  it('art arda taşımalar sırayla gönderilir; biri bitmeden diğeri gitmez', async () => {
    await create();

    component.drop(dropEvent('TODO', 0, 'DONE', 0)); // 1 → DONE
    component.drop(dropEvent('TODO', 0, 'DONE', 1)); // 2 → DONE sonu

    expect(tasks.move).toHaveBeenCalledTimes(1);
    expect(ids(component.board, 'DONE')).toEqual([1, 2]);

    moveResponses[0].next(task(1, 'DONE', 0));
    moveResponses[0].complete();
    expect(tasks.move).toHaveBeenCalledTimes(2);
    expect(tasks.move.calls.mostRecent().args).toEqual([2, 'DONE', 1]);

    moveResponses[1].next(task(2, 'DONE', 1));
    moveResponses[1].complete();
    expect(component.pendingMoves).toBe(0);
    expect(tasks.board).toHaveBeenCalledTimes(1);
  });

  it('ilk taşıma başarısız ve ikincisi bekliyorsa: geri almaz, kuyruk bitince sunucudan yükler', async () => {
    await create();
    component.drop(dropEvent('TODO', 0, 'DONE', 0));
    component.drop(dropEvent('TODO', 0, 'DONE', 1));

    moveResponses[0].error(new HttpErrorResponse({ status: 404, error: { message: 'Görev bulunamadı' } }));
    expect(tasks.board).toHaveBeenCalledTimes(1); // ikinci taşıma sürerken yüklenmez
    expect(ids(component.board, 'DONE')).toEqual([1, 2]);

    moveResponses[1].next(task(2, 'DONE', 0));
    moveResponses[1].complete();

    expect(tasks.board).toHaveBeenCalledTimes(2);
    expect(ids(component.board, 'TODO')).toEqual([1, 2, 3]); // sunucunun cevabı (sahte: başlangıç hâli)
  });

  it('sunucu görevi başka bir yere koyduysa pano yenilenir', async () => {
    await create();
    component.drop(dropEvent('TODO', 0, 'IN_PROGRESS', 0));

    moveResponses[0].next(task(1, 'IN_PROGRESS', 1)); // arada başkası da taşımış
    moveResponses[0].complete();

    expect(tasks.board).toHaveBeenCalledTimes(2);
  });

  it('arşivlenmiş projede taşıma kapalı', async () => {
    projects.get.and.returnValue(of({ ...PROJECT, status: 'ARCHIVED' }));
    await create();

    component.drop(dropEvent('TODO', 0, 'DONE', 0));

    expect(tasks.move).not.toHaveBeenCalled();
    expect(el().textContent).toContain('Bu proje arşivde');
    expect(column('TODO').classList).toContain('cdk-drop-list-disabled');
  });

  it('erişim yoksa sayfada açıklanır', async () => {
    tasks.board.and.returnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    await create();

    expect(el().textContent).toContain('Bu projeye erişimin yok');
    expect(el().querySelector('.kanban-board')).toBeNull();
  });
});
