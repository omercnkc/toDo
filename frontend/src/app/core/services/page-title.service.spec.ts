import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';

import { APP_NAME, PageTitleService } from './page-title.service';

@Component({ template: '<router-outlet></router-outlet>' })
class ShellComponent {}

@Component({ template: '' })
class PageComponent {}

describe('PageTitleService', () => {
  let router: Router;
  let title: Title;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ShellComponent, PageComponent],
      imports: [
        RouterTestingModule.withRoutes([
          {
            path: 'projects',
            component: ShellComponent,
            data: { title: 'Projeler' },
            children: [
              { path: '', component: PageComponent },
              { path: 'new', component: PageComponent, data: { title: 'Yeni proje' } },
            ],
          },
          { path: 'untitled', component: PageComponent },
        ]),
      ],
    });
    router = TestBed.inject(Router);
    title = TestBed.inject(Title);
    TestBed.inject(PageTitleService).start();
    TestBed.createComponent(ShellComponent);
  });

  it('en derindeki tanımlı başlığı uygulama adıyla birlikte yazar', async () => {
    await router.navigateByUrl('/projects/new');
    expect(title.getTitle()).toBe(`Yeni proje · ${APP_NAME}`);

    await router.navigateByUrl('/projects');
    expect(title.getTitle()).toBe(`Projeler · ${APP_NAME}`);
  });

  it('başlığı olmayan sayfada yalnızca uygulama adı', async () => {
    await router.navigateByUrl('/untitled');
    expect(title.getTitle()).toBe(APP_NAME);
  });
});
