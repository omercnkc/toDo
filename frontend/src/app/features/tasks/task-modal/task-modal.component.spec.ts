import { A11yModule } from '@angular/cdk/a11y';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';

import { ProjectMember } from '../../../core/models/project.model';
import { Task } from '../../../core/models/task.model';
import { TaskService } from '../../../core/services/task.service';
import { ToastService } from '../../../core/services/toast.service';
import { SharedModule } from '../../../shared/shared.module';
import { TaskModalComponent } from './task-modal.component';

const MEMBERS: ProjectMember[] = [
  { userId: 1, email: 'a@ornek.com', fullName: 'Ayşe Yılmaz', enabled: true, role: 'OWNER', joinedAt: '' },
  { userId: 2, email: 'b@ornek.com', fullName: 'Burak Kaya', enabled: true, role: 'MEMBER', joinedAt: '' },
];

const TASK: Task = {
  id: 42, projectId: 7, title: 'Giriş ekranı', description: 'Ayrıntı', status: 'TODO', priority: 'HIGH',
  position: 0, assigneeId: 2, reporterId: 1, dueDate: '2026-12-01', canDelete: true,
  createdAt: '2026-11-01T10:00:00Z', updatedAt: '2026-11-02T10:00:00Z',
};

describe('TaskModalComponent', () => {
  let fixture: ComponentFixture<TaskModalComponent>;
  let component: TaskModalComponent;
  let tasks: jasmine.SpyObj<TaskService>;
  let toast: jasmine.SpyObj<ToastService>;
  let changed: number;
  let closed: number;

  async function create(task: Task | null, readOnly = false): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [SharedModule, ReactiveFormsModule, A11yModule],
      declarations: [TaskModalComponent],
      providers: [
        { provide: TaskService, useValue: tasks },
        { provide: ToastService, useValue: toast },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TaskModalComponent);
    component = fixture.componentInstance;
    component.projectId = 7;
    component.task = task;
    component.members = MEMBERS;
    component.readOnly = readOnly;
    changed = 0;
    closed = 0;
    component.changed.subscribe(() => changed++);
    component.closed.subscribe(() => closed++);
    fixture.detectChanges();
  }

  function el(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function input(selector: string, value: string): void {
    const field = el().querySelector<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>(selector)!;
    field.value = value;
    field.dispatchEvent(new Event(field.tagName === 'SELECT' ? 'change' : 'input'));
  }

  function submit(): void {
    el().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
  }

  beforeEach(() => {
    tasks = jasmine.createSpyObj<TaskService>('TaskService', ['create', 'update', 'changeStatus', 'delete']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
  });

  afterEach(() => document.body.classList.remove('modal-open'));

  it('yeni görev: alanlar gönderilir, boşluklar kırpılır, boş alanlar null', async () => {
    tasks.create.and.returnValue(of(TASK));
    await create(null);
    expect(el().textContent).toContain('Yeni görev');
    expect(el().querySelector('#task-status')).toBeNull(); // yeni görev her zaman TODO
    expect(document.body.classList).toContain('modal-open');

    input('#task-title', '  Giriş ekranı  ');
    input('#task-priority', 'HIGH');
    input('#task-assignee', '2');
    input('#task-due', '2026-12-01');
    submit();

    expect(tasks.create).toHaveBeenCalledOnceWith({
      projectId: 7, title: 'Giriş ekranı', description: null, priority: 'HIGH', assigneeId: 2, dueDate: '2026-12-01',
    });
    expect(toast.success).toHaveBeenCalledWith('Görev oluşturuldu.');
    expect(changed).toBe(1);
    expect(closed).toBe(1);
  });

  it('boş veya yalnızca boşluk başlık gönderilmez', async () => {
    await create(null);

    input('#task-title', '   ');
    submit();

    expect(tasks.create).not.toHaveBeenCalled();
    expect(el().textContent).toContain('Bu alan zorunlu');
  });

  it('düzenleme: alanlar dolu gelir; durum değişirse içerikten sonra durum da kaydedilir', async () => {
    tasks.update.and.returnValue(of(TASK));
    tasks.changeStatus.and.returnValue(of({ ...TASK, status: 'DONE' }));
    await create(TASK);
    expect(el().querySelector<HTMLInputElement>('#task-title')!.value).toBe('Giriş ekranı');
    expect(el().querySelector<HTMLSelectElement>('#task-assignee')!.value).toBe('2');
    expect(el().textContent).toContain('Oluşturan: Ayşe Yılmaz');

    input('#task-title', 'Yeni başlık');
    input('#task-assignee', '');
    input('#task-status', 'DONE');
    submit();

    expect(tasks.update).toHaveBeenCalledOnceWith(42, {
      title: 'Yeni başlık', description: 'Ayrıntı', priority: 'HIGH', assigneeId: null, dueDate: '2026-12-01',
    });
    expect(tasks.changeStatus).toHaveBeenCalledOnceWith(42, 'DONE');
    expect(changed).toBe(1);
  });

  it('durum değişmediyse yalnızca içerik kaydedilir', async () => {
    tasks.update.and.returnValue(of(TASK));
    await create(TASK);

    submit();

    expect(tasks.update).toHaveBeenCalled();
    expect(tasks.changeStatus).not.toHaveBeenCalled();
  });

  it('sunucu alan hatası alanın altında, modal açık kalır', async () => {
    tasks.update.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 400,
      error: { error: 'ASSIGNEE_NOT_MEMBER', message: 'Görev yalnızca proje üyelerine atanabilir',
        fieldErrors: [{ field: 'assigneeId', message: 'proje üyesi değil' }] },
    })));
    await create(TASK);

    submit();

    expect(el().textContent).toContain('Proje üyesi değil');
    expect(closed).toBe(0);
  });

  it('arşivlenmiş projede 409: mesaj gösterilir ve pano yenilenir', async () => {
    tasks.update.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409, error: { error: 'PROJECT_ARCHIVED', message: 'Arşivlenmiş projenin görevleri değiştirilemez' },
    })));
    await create(TASK);

    submit();

    expect(el().querySelector('.alert-danger')!.textContent).toContain('Arşivlenmiş projenin görevleri değiştirilemez');
    expect(changed).toBe(1);
    expect(closed).toBe(0);
  });

  it('silme onay ister; onaylanınca siler', async () => {
    tasks.delete.and.returnValue(of(undefined));
    await create(TASK);
    const confirmSpy = spyOn(window, 'confirm').and.returnValues(false, true);
    const deleteButton = () => Array.from(el().querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => b.textContent?.trim() === 'Sil')!;

    deleteButton().click();
    expect(tasks.delete).not.toHaveBeenCalled();

    deleteButton().click();
    expect(confirmSpy).toHaveBeenCalledTimes(2);
    expect(tasks.delete).toHaveBeenCalledOnceWith(42);
    expect(toast.success).toHaveBeenCalledWith('Görev silindi.');
    expect(changed).toBe(1);
    expect(closed).toBe(1);
  });

  it('silme yetkisi yoksa Sil düğmesi yok; çıkarılmış atanan "üye değil" olarak görünür', async () => {
    await create({ ...TASK, canDelete: false, assigneeId: 99 });

    expect(el().textContent).not.toMatch(/\bSil\b/);
    expect(el().querySelector<HTMLSelectElement>('#task-assignee')!.value).toBe('99');
    expect(el().textContent).toContain('Kullanıcı #99 (üye değil)');
  });

  it('arşivde salt okunur: alanlar kilitli, kaydet/sil yok', async () => {
    await create(TASK, true);

    expect(el().querySelector<HTMLInputElement>('#task-title')!.disabled).toBeTrue();
    expect(el().textContent).toContain('Proje arşivde');
    expect(el().textContent).not.toContain('Kaydet');
    expect(el().textContent).not.toMatch(/\bSil\b/);
  });

  it('Esc ve arka plan tıklaması kapatır; diyalog içine tıklama kapatmaz; kapanınca sayfa kilidi kalkar', async () => {
    await create(TASK);

    el().querySelector<HTMLElement>('.modal-content')!.click();
    expect(closed).toBe(0);
    el().querySelector<HTMLElement>('.modal')!.click();
    expect(closed).toBe(1);
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(closed).toBe(2);

    fixture.destroy();
    expect(document.body.classList).not.toContain('modal-open');
  });
});
