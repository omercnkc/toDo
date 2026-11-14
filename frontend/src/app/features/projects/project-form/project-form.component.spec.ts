import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';

import { Project } from '../../../core/models/project.model';
import { ProjectService } from '../../../core/services/project.service';
import { ToastService } from '../../../core/services/toast.service';
import { SharedModule } from '../../../shared/shared.module';
import { ProjectFormComponent } from './project-form.component';

const PROJECT: Project = {
  id: 7, name: 'Web', description: 'Site', status: 'ACTIVE', ownerId: 1, myRole: 'OWNER', canManage: true,
  memberCount: 1, createdAt: '2026-11-01T10:00:00Z', updatedAt: '2026-11-01T10:00:00Z',
};

describe('ProjectFormComponent', () => {
  let fixture: ComponentFixture<ProjectFormComponent>;
  let component: ProjectFormComponent;
  let projects: jasmine.SpyObj<ProjectService>;
  let toast: jasmine.SpyObj<ToastService>;
  let router: Router;

  async function create(id: string | null): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, ReactiveFormsModule, SharedModule],
      declarations: [ProjectFormComponent],
      providers: [
        { provide: ProjectService, useValue: projects },
        { provide: ToastService, useValue: toast },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } } },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(ProjectFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function text(): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  beforeEach(() => {
    projects = jasmine.createSpyObj<ProjectService>('ProjectService', ['get', 'create', 'update']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
  });

  describe('oluşturma', () => {
    beforeEach(async () => create(null));

    it('boş veya yalnızca boşluk ad gönderilmez', () => {
      component.submit();
      component.form.setValue({ name: '   ', description: '' });
      component.submit();

      expect(projects.create).not.toHaveBeenCalled();
      expect(text()).toContain('Bu alan zorunlu');
    });

    it('kırpılmış değerlerle oluşturur, boş açıklama null gider', () => {
      projects.create.and.returnValue(of(PROJECT));
      component.form.setValue({ name: '  Web  ', description: '   ' });

      component.submit();

      expect(projects.create).toHaveBeenCalledWith({ name: 'Web', description: null });
      expect(toast.success).toHaveBeenCalledWith('"Web" oluşturuldu.');
      expect(router.navigate).toHaveBeenCalledWith(['/projects', 7]);
    });

    it('açıklama sayacı ve sınır', () => {
      component.form.setValue({ name: 'A', description: 'x'.repeat(5001) });
      component.form.markAllAsTouched();

      expect(text()).toContain('5001 / 5000');
      expect(text()).toContain('En fazla 5000 karakter olabilir');
      component.submit();
      expect(projects.create).not.toHaveBeenCalled();
    });

    it('sunucu alan hatası alanın altında', () => {
      projects.create.and.returnValue(throwError(() => new HttpErrorResponse({
        status: 400,
        error: { status: 400, error: 'VALIDATION_ERROR', message: 'Doğrulama hatası', path: '', timestamp: '',
          fieldErrors: [{ field: 'name', message: 'boş olamaz' }] },
      })));
      component.form.setValue({ name: 'A', description: '' });

      component.submit();

      expect(component.formError).toBeNull();
      expect(text()).toContain('Boş olamaz');
    });
  });

  describe('düzenleme', () => {
    it('projeyi yükler ve günceller', async () => {
      projects.get.and.returnValue(of(PROJECT));
      await create('7');

      expect(projects.get).toHaveBeenCalledWith(7);
      expect(component.form.value).toEqual({ name: 'Web', description: 'Site' });
      expect(text()).toContain('Projeyi düzenle');

      projects.update.and.returnValue(of({ ...PROJECT, name: 'Web 2' }));
      component.form.patchValue({ name: 'Web 2' });
      component.submit();

      expect(projects.update).toHaveBeenCalledWith(7, { name: 'Web 2', description: 'Site' });
      expect(toast.success).toHaveBeenCalledWith('Proje güncellendi.');
      expect(router.navigate).toHaveBeenCalledWith(['/projects', 7]);
    });

    it('yetkisi yoksa veya arşivdeyse form kilitlenir', async () => {
      projects.get.and.returnValue(of({ ...PROJECT, canManage: false, myRole: 'MEMBER' }));
      await create('7');

      expect(component.form.disabled).toBeTrue();
      expect(text()).toContain('yalnızca proje sahibi veya yönetici');
      component.submit();
      expect(projects.update).not.toHaveBeenCalled();

      component.lockedReason = null;
      component.form.enable();
      (component as unknown as { setProject(p: Project): void }).setProject({ ...PROJECT, status: 'ARCHIVED' });
      expect(text()).toContain('Arşivlenmiş proje düzenlenemez');
    });

    it('proje yoksa listeye döner', async () => {
      projects.get.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
      await create('7');

      expect(toast.error).toHaveBeenCalledWith('Proje bulunamadı.');
      expect(router.navigate).toHaveBeenCalledWith(['/projects']);
    });
  });
});
