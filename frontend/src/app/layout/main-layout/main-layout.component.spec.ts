import { Component, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';

import { MainLayoutComponent } from './main-layout.component';

@Component({ template: '<p>sayfa</p>' })
class PageComponent {}

describe('MainLayoutComponent', () => {
  let fixture: ComponentFixture<MainLayoutComponent>;
  let router: Router;
  let main: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [MainLayoutComponent, PageComponent],
      imports: [
        RouterTestingModule.withRoutes([
          { path: 'a', component: PageComponent },
          { path: 'b', component: PageComponent },
        ]),
      ],
      schemas: [NO_ERRORS_SCHEMA], // app-navbar
    }).compileComponents();
    router = TestBed.inject(Router);
    await router.navigateByUrl('/a');
    fixture = TestBed.createComponent(MainLayoutComponent);
    fixture.detectChanges();
    document.body.appendChild(fixture.nativeElement);
    main = fixture.nativeElement.querySelector('main');
    spyOn(window, 'scrollTo');
  });

  afterEach(() => (fixture.nativeElement as HTMLElement).remove());

  it('yol değişince odağı içeriğe taşır ve başa kayar', async () => {
    await router.navigateByUrl('/b');

    expect(document.activeElement).toBe(main);
    expect(window.scrollTo).toHaveBeenCalledWith(0, 0);
  });

  it('yalnızca sorgu parametresi değişince (filtre, sayfa) odağa ve kaydırmaya dokunmaz', async () => {
    const button = document.createElement('button');
    document.body.appendChild(button);
    button.focus();

    await router.navigateByUrl('/a?page=2');

    expect(document.activeElement).toBe(button);
    expect(window.scrollTo).not.toHaveBeenCalled();
    button.remove();
  });

  it('"İçeriğe atla" odağı içeriğe taşır, gezinme yapmaz', () => {
    const navigate = spyOn(router, 'navigateByUrl').and.callThrough();
    fixture.nativeElement.querySelector('.skip-link').click();

    expect(document.activeElement).toBe(main);
    expect(navigate).not.toHaveBeenCalled();
  });
});
