import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { Invitation, Project, ProjectMember } from '../../../core/models/project.model';
import { ProjectService } from '../../../core/services/project.service';
import { ToastService } from '../../../core/services/toast.service';
import { UserService } from '../../../core/services/user.service';
import { SharedModule } from '../../../shared/shared.module';
import { InvitePanelComponent } from '../invite-panel/invite-panel.component';
import { ProjectDetailComponent } from './project-detail.component';

const PROJECT: Project = {
  id: 7, name: 'Web Sitesi', description: 'Satır 1\nSatır 2', status: 'ACTIVE', ownerId: 1, myRole: 'OWNER',
  canManage: true, memberCount: 2, createdAt: '2026-11-01T10:00:00Z', updatedAt: '2026-11-01T10:00:00Z',
};
const MEMBERS: ProjectMember[] = [
  { userId: 1, email: 'sahip@ornek.com', fullName: 'Sahip Kişi', enabled: true, role: 'OWNER', joinedAt: '2026-11-01T10:00:00Z' },
  { userId: 2, email: null, fullName: null, enabled: null, role: 'MEMBER', joinedAt: '2026-11-02T10:00:00Z' },
];
const INVITATION: Invitation = {
  id: 50, status: 'PENDING', createdAt: '2026-11-03T10:00:00Z', respondedAt: null,
  project: { id: 7, name: 'Web Sitesi' },
  inviter: { id: 1, fullName: 'Sahip Kişi', email: 'sahip@ornek.com' },
  invitee: { id: 9, fullName: 'Davetli Kişi', email: 'davetli@ornek.com' },
};

describe('ProjectDetailComponent', () => {
  let fixture: ComponentFixture<ProjectDetailComponent>;
  let component: ProjectDetailComponent;
  let projects: jasmine.SpyObj<ProjectService>;
  let toast: jasmine.SpyObj<ToastService>;

  async function create(id = '7'): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, ReactiveFormsModule, SharedModule],
      declarations: [ProjectDetailComponent, InvitePanelComponent],
      providers: [
        { provide: ProjectService, useValue: projects },
        { provide: ToastService, useValue: toast },
        { provide: UserService, useValue: jasmine.createSpyObj('UserService', ['search']) },
        { provide: AuthService, useValue: { currentUser: { id: 1 } } },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id })) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ProjectDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function el(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function byLabel(label: string): HTMLButtonElement | null {
    return el().querySelector<HTMLButtonElement>(`[aria-label="${label}"]`);
  }

  beforeEach(() => {
    projects = jasmine.createSpyObj<ProjectService>('ProjectService',
      ['get', 'members', 'invitations', 'archive', 'restore', 'removeMember', 'invite', 'cancelInvitation']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
    projects.get.and.returnValue(of(PROJECT));
    projects.members.and.returnValue(of(MEMBERS));
    projects.invitations.and.returnValue(of([INVITATION]));
  });

  it('sahip: bilgiler, üyeler, davet paneli ve bekleyen davetler', async () => {
    await create();
    const content = el().textContent ?? '';

    expect(projects.get).toHaveBeenCalledWith(7, true); // genel bildirim kapalı
    expect(projects.invitations).toHaveBeenCalledWith(7, 'PENDING');
    expect(content).toContain('Web Sitesi');
    expect(content).toContain('Sahip Kişi');
    expect(content).toContain('Kullanıcı #2'); // ad alınamayan üye
    expect(content).toContain('E-posta bilgisi alınamadı');
    expect(content).toContain('Davetli Kişi');
    expect(el().querySelector('app-invite-panel')).not.toBeNull();
    expect(byLabel('Projeden çıkar: Kullanıcı #2')).not.toBeNull();
    expect(byLabel('Projeden çıkar: Sahip Kişi')).toBeNull(); // OWNER çıkarılamaz
    expect(component.memberIds).toEqual([1, 2]);
    expect(component.pendingInviteeIds).toEqual([9]);
  });

  it('üye: yönetim düğmeleri ve davet paneli yok, davetler istenmez', async () => {
    projects.get.and.returnValue(of({ ...PROJECT, myRole: 'MEMBER', canManage: false }));
    await create();

    expect(projects.invitations).not.toHaveBeenCalled();
    expect(el().querySelector('app-invite-panel')).toBeNull();
    expect(byLabel('Projeden çıkar: Kullanıcı #2')).toBeNull();
    expect(el().textContent).not.toContain('Düzenle');
  });

  it('arşivde: üyelik değişmez, arşivden çıkar düğmesi', async () => {
    projects.get.and.returnValue(of({ ...PROJECT, status: 'ARCHIVED' }));
    projects.restore.and.returnValue(of(PROJECT));
    await create();

    expect(el().textContent).toContain('Bu proje arşivde');
    expect(el().querySelector('app-invite-panel')).toBeNull();
    expect(byLabel('Projeden çıkar: Kullanıcı #2')).toBeNull();

    Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Arşivden çıkar'))!.click();
    expect(projects.restore).toHaveBeenCalledWith(jasmine.objectContaining({ id: 7 }));
    expect(toast.success).toHaveBeenCalled();
  });

  it('erişim yoksa veya proje yoksa sayfada açıklar', async () => {
    projects.get.and.returnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    await create();
    expect(el().textContent).toContain('Bu projeye erişimin yok');
    expect(projects.members).not.toHaveBeenCalled();
  });

  it('geçersiz id için istek atmadan "bulunamadı"', async () => {
    await create('abc');
    expect(projects.get).not.toHaveBeenCalled();
    expect(el().textContent).toContain('Proje bulunamadı');
  });

  it('üye çıkarma onay ister, sonra yeniden yükler', async () => {
    await create();
    const confirmSpy = spyOn(window, 'confirm').and.returnValue(false);
    byLabel('Projeden çıkar: Kullanıcı #2')!.click();
    expect(projects.removeMember).not.toHaveBeenCalled();

    confirmSpy.and.returnValue(true);
    projects.removeMember.and.returnValue(of(undefined));
    byLabel('Projeden çıkar: Kullanıcı #2')!.click();

    expect(projects.removeMember).toHaveBeenCalledWith(7, 2);
    expect(toast.success).toHaveBeenCalledWith('Kullanıcı #2 projeden çıkarıldı.');
    expect(projects.get).toHaveBeenCalledTimes(2);
  });

  it('davet gönderir; 409 mesajını bildirip sayfayı tazeler', async () => {
    await create();
    projects.invite.and.returnValue(of(INVITATION));
    component.invite({ id: 9, email: 'd@ornek.com', fullName: 'Davetli Kişi' });
    expect(projects.invite).toHaveBeenCalledWith(7, 9);
    expect(toast.success).toHaveBeenCalledWith('Davetli Kişi davet edildi.');

    projects.invite.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: { status: 409, error: 'INVITATION_RECENTLY_REJECTED', message: 'Son 24 saatte reddetti', path: '', timestamp: '' },
    })));
    component.invite({ id: 10, email: 'r@ornek.com', fullName: 'Reddeden' });
    expect(toast.error).toHaveBeenCalledWith('Son 24 saatte reddetti');
    expect(component.busy.invitingUserId).toBeNull();
    expect(projects.get).toHaveBeenCalledTimes(3);
  });

  it('bekleyen daveti geri çeker', async () => {
    await create();
    spyOn(window, 'confirm').and.returnValue(true);
    projects.cancelInvitation.and.returnValue(of(undefined));

    byLabel('Daveti geri çek: Davetli Kişi')!.click();

    expect(projects.cancelInvitation).toHaveBeenCalledWith(7, 50);
    expect(toast.success).toHaveBeenCalledWith('Davet geri çekildi.');
  });
});
