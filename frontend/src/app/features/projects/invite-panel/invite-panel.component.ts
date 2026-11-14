import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Observable, of, Subject } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, takeUntil, tap } from 'rxjs/operators';

import { UserSummary } from '../../../core/models/user.model';
import { UserService } from '../../../core/services/user.service';

/** Backend kuralı: USER aramasında en az 3 karakter (docs/06). */
export const INVITE_SEARCH_MIN = 3;

export type SearchState = 'idle' | 'tooShort' | 'loading' | 'done' | 'error';
export type CandidateStatus = 'self' | 'member' | 'pending' | 'invitable';

/**
 * Projeye davet için kullanıcı arama (ad veya e-posta). Yazmayı bırakınca 300 ms sonra arar; yeni arama
 * eskisini iptal eder. Sonuçta zaten üye olan, bekleyen daveti olan ve kişinin kendisi işaretlenir.
 * Davet isteğini üst bileşen gönderir ({@link invite} olayı).
 */
@Component({
  selector: 'app-invite-panel',
  templateUrl: './invite-panel.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InvitePanelComponent implements OnInit, OnDestroy {
  @Input() memberIds: number[] = [];
  @Input() pendingInviteeIds: number[] = [];
  @Input() currentUserId: number | null = null;
  /** Daveti gönderilmekte olan kullanıcı (düğmesi kilitlenir). */
  @Input() invitingUserId: number | null = null;
  @Output() readonly invite = new EventEmitter<UserSummary>();

  readonly minLength = INVITE_SEARCH_MIN;
  readonly search = new FormControl('');
  state: SearchState = 'idle';
  results: UserSummary[] = [];

  private readonly destroy$ = new Subject<void>();

  constructor(private readonly users: UserService, private readonly cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.search.valueChanges
      .pipe(
        map((value: string | null) => (value ?? '').trim()),
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.run(q)),
        takeUntil(this.destroy$),
      )
      .subscribe();
  }

  status(user: UserSummary): CandidateStatus {
    if (user.id === this.currentUserId) {
      return 'self';
    }
    if (this.memberIds.includes(user.id)) {
      return 'member';
    }
    return this.pendingInviteeIds.includes(user.id) ? 'pending' : 'invitable';
  }

  trackById(_index: number, user: UserSummary): number {
    return user.id;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private run(q: string): Observable<unknown> {
    if (q.length === 0) {
      return this.setState('idle', []);
    }
    if (q.length < INVITE_SEARCH_MIN) {
      return this.setState('tooShort', []);
    }
    this.state = 'loading';
    this.cdr.markForCheck();
    return this.users.search(q).pipe(
      tap((results) => {
        this.results = results;
        this.state = 'done';
        this.cdr.markForCheck();
      }),
      catchError(() => this.setState('error', [])),
    );
  }

  private setState(state: SearchState, results: UserSummary[]): Observable<null> {
    this.state = state;
    this.results = results;
    this.cdr.markForCheck();
    return of(null);
  }
}
