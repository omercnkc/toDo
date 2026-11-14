import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { Page } from '../../../core/models/page.model';
import { Project } from '../../../core/models/project.model';
import { ProjectService } from '../../../core/services/project.service';
import { ToastService } from '../../../core/services/toast.service';
import { SharedModule } from '../../../shared/shared.module';
import { PROJECT_PAGE_SIZE, ProjectListComponent, toProjectListQuery } from './project-list.component';

function project(id: number, extra: Partial<Project> = {}): Project {
  return {
    id, name: `Proje ${id}`, description: 'Açıklama', status: 'ACTIVE', ownerId: 1, myRole: 'OWNER',
    canManage: true, memberCount: 2, createdAt: '2026-11-01T10:00:00Z', updatedAt: '2026-11-10T10:00:00Z', ...extra,
  };
}

function page(content: Project[], totalElements = content.length): Page<Project> {
  return { content, page: 0, size: PROJECT_PAGE_SIZE, totalElements, totalPages: Math.ceil(totalElements / PROJECT_PAGE_SIZE) };
}

describe('ProjectListComponent', () => {
  let fixture: ComponentFixture<ProjectListComponent>;
  let component: ProjectListComponent;
  let projects: jasmine.SpyObj<ProjectService>;
  let toast: jasmine.SpyObj<ToastService>;
  let router: Router;
  let queryParams$: BehaviorSubject<ParamMap>;

  function el(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function buttonLabelled(label: string): HTMLButtonElement | null {
    return el().querySelector<HTMLButtonElement>(`[aria-label="${label}"]`);
  }

  beforeEach(async () => {
    projects = jasmine.createSpyObj<ProjectService>('ProjectService', ['list', 'archive', 'restore']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
    queryParams$ = new BehaviorSubject(convertToParamMap({}));
    projects.list.and.returnValue(of(page([
      project(1),
      project(2, { myRole: 'MEMBER', canManage: false, description: null }),
      project(3, { myRole: null }),
    ])));

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, ReactiveFormsModule, SharedModule],
      declarations: [ProjectListComponent],
      providers: [
        { provide: ProjectService, useValue: projects },
        { provide: ToastService, useValue: toast },
        { provide: ActivatedRoute, useValue: { queryParamMap: queryParams$.asObservable() } },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(ProjectListComponent);
    component = fixture.componentInstance;
  });

  it('varsayılan aktif projeleri ister; URL filtrelerini uygular', () => {
    fixture.detectChanges();
    expect(projects.list).toHaveBeenCalledWith({ q: '', status: 'ACTIVE', page: 0, size: PROJECT_PAGE_SIZE });

    queryParams$.next(convertToParamMap({ q: 'web', status: 'ALL', page: '2' }));
    expect(projects.list).toHaveBeenCalledWith({ q: 'web', status: null, page: 1, size: PROJECT_PAGE_SIZE });
    expect(component.search.value).toBe('web');
  });

  it('kartlarda rol, üye sayısı; düzenleme düğmeleri yalnızca yönetebilene', () => {
    const content = el().textContent ?? '';

    expect(content).toContain('3 proje');
    expect(content).toContain('Sahibi');
    expect(content).toContain('Üye');
    expect(content).toContain('Yönetici erişimi');
    expect(content).toContain('Açıklama yok.');
    expect(buttonLabelled('Arşivle: Proje 1')).not.toBeNull();
    expect(buttonLabelled('Arşivle: Proje 2')).toBeNull();
    expect(el().querySelector('[aria-label="Düzenle: Proje 1"]')?.getAttribute('href')).toBe('/projects/1/edit');
  });

  it('arama 300 ms bekler, durum filtresi korunur', fakeAsync(() => {
    queryParams$.next(convertToParamMap({ status: 'ARCHIVED' }));
    fixture.detectChanges();
    component.search.setValue(' mobil ');
    tick(300);

    expect(router.navigate).toHaveBeenCalledOnceWith([], jasmine.objectContaining({
      queryParams: { q: 'mobil', status: 'ARCHIVED', page: null },
      replaceUrl: true,
    }));
  }));

  it('durum sekmesi URL\'ye yazılır, aktif varsayılan olduğu için yazılmaz', () => {
    fixture.detectChanges();
    el().querySelector<HTMLInputElement>('#status-ARCHIVED')!.dispatchEvent(new Event('change'));
    expect(router.navigate).toHaveBeenCalledWith([], jasmine.objectContaining({
      queryParams: { q: null, status: 'ARCHIVED', page: null },
    }));

    queryParams$.next(convertToParamMap({ status: 'ARCHIVED' }));
    component.setStatus('ACTIVE');
    expect(router.navigate).toHaveBeenCalledWith([], jasmine.objectContaining({
      queryParams: { q: null, status: null, page: null },
    }));
  });

  it('arşivleme onay ister, bildirim gösterip listeyi yeniler', () => {
    fixture.detectChanges();
    const confirmSpy = spyOn(window, 'confirm').and.returnValue(false);
    buttonLabelled('Arşivle: Proje 1')!.click();
    expect(projects.archive).not.toHaveBeenCalled();

    confirmSpy.and.returnValue(true);
    projects.archive.and.returnValue(of(undefined));
    buttonLabelled('Arşivle: Proje 1')!.click();

    expect(projects.archive).toHaveBeenCalledWith(1);
    expect(toast.success).toHaveBeenCalledWith('"Proje 1" arşivlendi.');
    expect(projects.list).toHaveBeenCalledTimes(2);
  });

  it('arşivdeki projede arşivden çıkar düğmesi', () => {
    projects.list.and.returnValue(of(page([project(9, { status: 'ARCHIVED' })])));
    projects.restore.and.returnValue(of(project(9)));
    queryParams$.next(convertToParamMap({ status: 'ARCHIVED' }));

    expect(el().textContent).toContain('Arşivde');
    expect(buttonLabelled('Arşivle: Proje 9')).toBeNull();
    buttonLabelled('Arşivden çıkar: Proje 9')!.click();

    expect(projects.restore).toHaveBeenCalledWith(jasmine.objectContaining({ id: 9 }));
    expect(toast.success).toHaveBeenCalledWith('"Proje 9" arşivden çıkarıldı.');
  });

  it('işlem 409 dönerse sunucu mesajını bildirir', () => {
    fixture.detectChanges();
    spyOn(window, 'confirm').and.returnValue(true);
    projects.archive.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409, error: { status: 409, error: 'X', message: 'Olmaz', path: '', timestamp: '' },
    })));

    buttonLabelled('Arşivle: Proje 1')!.click();

    expect(toast.error).toHaveBeenCalledWith('Olmaz');
    expect(component.pendingId).toBeNull();
  });

  it('hiç proje yoksa ilk proje çağrısı, aramada eşleşme yoksa ayrı mesaj', () => {
    projects.list.and.returnValue(of(page([])));
    fixture.detectChanges();
    expect(el().textContent).toContain('İlk projeni oluştur');

    queryParams$.next(convertToParamMap({ q: 'xyz' }));
    expect(el().textContent).toContain('“xyz” ile eşleşen proje yok.');
    expect(el().textContent).not.toContain('İlk projeni oluştur');
  });

  it('yüklenemezse tekrar dene', () => {
    projects.list.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    expect(el().textContent).toContain('Projeler yüklenemedi');

    projects.list.and.returnValue(of(page([project(1)])));
    component.retry();
    expect(el().textContent).not.toContain('Projeler yüklenemedi');
  });

  it('toProjectListQuery bilinmeyen durumu aktif sayar', () => {
    expect(toProjectListQuery(convertToParamMap({ status: 'SILINDI', page: '0' })))
      .toEqual({ q: '', status: 'ACTIVE', page: 0 });
    expect(toProjectListQuery(convertToParamMap({ status: 'ARCHIVED', page: '3', q: ' a ' })))
      .toEqual({ q: 'a', status: 'ARCHIVED', page: 2 });
  });
});
