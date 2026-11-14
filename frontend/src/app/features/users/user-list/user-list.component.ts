import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { Observable, of, Subject } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, takeUntil, tap } from 'rxjs/operators';

import { AuthService } from '../../../core/auth/auth.service';
import { extractApiError } from '../../../core/models/api-error.model';
import { Page } from '../../../core/models/page.model';
import { Role, UserDetail } from '../../../core/models/user.model';
import { ToastService } from '../../../core/services/toast.service';
import { AdminUpdateUserRequest, UserService } from '../../../core/services/user.service';

export const PAGE_SIZE = 20;

interface ListQuery {
  q: string;
  /** 0 tabanlı (backend); URL'de 1 tabanlı `page` görünür. */
  page: number;
}

/**
 * ADMIN kullanıcı yönetimi (docs/06 `GET /users`, `PUT /users/{id}`).
 *
 * - Arama ve sayfa URL'de (`?q=ali&page=2`): sayfa yenilense, geri gelinse de liste aynı kalır.
 *   Arama kutusu 300 ms bekler; yeni arama gelince eski istek iptal edilir (`switchMap`).
 * - ADMIN kendi rolünü/durumunu değiştiremez (backend de 409 verir); kendi satırında düğmeler yok.
 * - Pasifleştirme ve ADMIN yetkisi verme onay ister. Pasifleştirilen kişinin oturumları sunucuda kapanır.
 */
@Component({
  selector: 'app-user-list',
  templateUrl: './user-list.component.html',
  styles: ['.search-box { width: 100%; max-width: 22rem; }'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserListComponent implements OnInit, OnDestroy {
  readonly search = new FormControl('');
  readonly currentUserId = this.auth.currentUser?.id ?? null;

  result: Page<UserDetail> | null = null;
  loading = true;
  loadFailed = false;
  query: ListQuery = { q: '', page: 0 };
  /** İşlemi süren kullanıcı (o satırın düğmeleri kilitlenir). */
  pendingId: number | null = null;

  private readonly reload$ = new Subject<void>();
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly users: UserService,
    private readonly auth: AuthService,
    private readonly toast: ToastService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    // URL → liste
    this.route.queryParamMap
      .pipe(
        map(toQuery),
        distinctUntilChanged((a, b) => a.q === b.q && a.page === b.page),
        tap((query) => {
          this.query = query;
          if (this.search.value !== query.q) {
            this.search.setValue(query.q, { emitEvent: false });
          }
        }),
        switchMap((query) => this.fetch(query)),
        takeUntil(this.destroy$),
      )
      .subscribe();

    // "Tekrar dene"
    this.reload$
      .pipe(
        switchMap(() => this.fetch(this.query)),
        takeUntil(this.destroy$),
      )
      .subscribe();

    // Arama kutusu → URL (geçmişi her harfle doldurmamak için replaceUrl)
    this.search.valueChanges
      .pipe(
        map((value: string | null) => (value ?? '').trim()),
        debounceTime(300),
        distinctUntilChanged(),
        takeUntil(this.destroy$),
      )
      .subscribe((q) => {
        if (q !== this.query.q) {
          this.navigate({ q, page: 0 }, true);
        }
      });
  }

  retry(): void {
    this.reload$.next();
  }

  /** Geçerlilik kontrolünü PaginationComponent yapar. */
  goToPage(page: number): void {
    this.navigate({ ...this.query, page }, false);
  }

  isSelf(user: UserDetail): boolean {
    return user.id === this.currentUserId;
  }

  changeRole(user: UserDetail, role: Role): void {
    if (role === 'ADMIN' && !confirm(`${user.fullName} kullanıcısına yönetici yetkisi verilsin mi?`)) {
      return;
    }
    this.update(user, { role }, role === 'ADMIN' ? 'yönetici yapıldı' : 'artık kullanıcı rolünde');
  }

  setEnabled(user: UserDetail, enabled: boolean): void {
    if (!enabled && !confirm(`${user.fullName} pasifleştirilsin mi? Açık oturumları kapatılır ve giriş yapamaz.`)) {
      return;
    }
    this.update(user, { enabled }, enabled ? 'aktifleştirildi' : 'pasifleştirildi');
  }

  trackById(_index: number, user: UserDetail): number {
    return user.id;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private fetch(query: ListQuery): Observable<unknown> {
    this.loading = true;
    this.loadFailed = false;
    this.cdr.markForCheck();
    return this.users.list(query.q, query.page, PAGE_SIZE).pipe(
      tap((result) => {
        this.loading = false;
        // Son sayfadaki tek kayıt aramayla kaybolduysa vb.: boş bir sayfada kalma
        if (result.content.length === 0 && query.page > 0 && result.totalPages > 0) {
          this.navigate({ ...query, page: result.totalPages - 1 }, true);
          return;
        }
        this.result = result;
        this.cdr.markForCheck();
      }),
      catchError(() => {
        this.loading = false;
        this.loadFailed = true;
        this.cdr.markForCheck();
        return of(null);
      }),
    );
  }

  private update(user: UserDetail, request: AdminUpdateUserRequest, doneText: string): void {
    this.pendingId = user.id;
    this.users.adminUpdate(user.id, request).subscribe({
      next: (updated) => {
        this.pendingId = null;
        if (this.result) {
          this.result = {
            ...this.result,
            content: this.result.content.map((u) => (u.id === updated.id ? updated : u)),
          };
        }
        this.toast.success(`${updated.fullName} ${doneText}.`);
        this.cdr.markForCheck();
      },
      error: (error: unknown) => {
        this.pendingId = null;
        // 0/5xx/403'ü ErrorInterceptor gösterir; 400/404/409 mesajını burada göster
        if (error instanceof HttpErrorResponse && error.status > 0 && error.status < 500 && error.status !== 403) {
          this.toast.error(extractApiError(error)?.message ?? 'İşlem tamamlanamadı.');
        }
        this.cdr.markForCheck();
      },
    });
  }

  private navigate(query: ListQuery, replaceUrl: boolean): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { q: query.q || null, page: query.page > 0 ? query.page + 1 : null },
      replaceUrl,
    });
  }
}

/** URL'deki `q` ve 1 tabanlı `page` → sorgu. Geçersiz sayfa numarası ilk sayfa sayılır. */
export function toQuery(params: ParamMap): ListQuery {
  const page = Number(params.get('page'));
  return {
    q: (params.get('q') ?? '').trim(),
    page: Number.isInteger(page) && page > 1 ? page - 1 : 0,
  };
}
