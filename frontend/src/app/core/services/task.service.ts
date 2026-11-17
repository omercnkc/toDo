import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_TOAST } from '../interceptors/http-context.tokens';
import { Page } from '../models/page.model';
import { Board, CreateTaskRequest, Task, TaskListParams, TaskStatus, UpdateTaskRequest } from '../models/task.model';

/** `/api/tasks/**` (docs/06 "task-service"). Yetki backend'de; arayüz `canDelete`'e göre düğme gösterir. */
@Injectable({ providedIn: 'root' })
export class TaskService {
  private readonly tasksUrl = `${environment.apiUrl}/tasks`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Kanban: üç kolon, pozisyona göre sıralı.
   * @param silent true → 403/404 için genel bildirim gösterilmez (sayfa hatayı kendisi yazar)
   */
  board(projectId: number, silent = false): Observable<Board> {
    const params = new HttpParams().set('projectId', projectId);
    const context = new HttpContext().set(SKIP_ERROR_TOAST, silent);
    return this.http.get<Board>(`${this.tasksUrl}/board`, { params, context });
  }

  /**
   * Filtreli, sıralı, sayfalı liste (docs/06 "Liste filtreleri"). Boş filtreler gönderilmez.
   * @param silent true → 403/404 için genel bildirim gösterilmez (sayfa hatayı kendisi yazar)
   */
  list(query: TaskListParams, silent = false): Observable<Page<Task>> {
    let params = new HttpParams();
    const set = (key: string, value: string | number | null | undefined) => {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, value);
      }
    };
    set('projectId', query.projectId);
    set('status', query.status?.join(','));
    set('priority', query.priority?.join(','));
    set('assigneeId', query.assigneeId);
    set('q', query.q?.trim());
    set('dueFrom', query.dueFrom);
    set('dueTo', query.dueTo);
    set('overdue', query.overdue ? 'true' : null);
    for (const sort of query.sort ?? []) {
      params = params.append('sort', `${sort.field},${sort.direction}`);
    }
    set('page', query.page);
    set('size', query.size);
    const context = new HttpContext().set(SKIP_ERROR_TOAST, silent);
    return this.http.get<Page<Task>>(this.tasksUrl, { params, context });
  }

  get(id: number): Observable<Task> {
    return this.http.get<Task>(`${this.tasksUrl}/${id}`);
  }

  /** Yeni görev `TODO` kolonunun sonuna eklenir. */
  create(request: CreateTaskRequest): Observable<Task> {
    return this.http.post<Task>(this.tasksUrl, request);
  }

  update(id: number, request: UpdateTaskRequest): Observable<Task> {
    return this.http.put<Task>(`${this.tasksUrl}/${id}`, request);
  }

  /** Hedef kolonun sonuna taşır. */
  changeStatus(id: number, status: TaskStatus): Observable<Task> {
    return this.http.patch<Task>(`${this.tasksUrl}/${id}/status`, { status });
  }

  /** Sürükle-bırak: hedef kolon + kolondaki sıra (sunucu kolon boyuna sınırlar). */
  move(id: number, status: TaskStatus, position: number): Observable<Task> {
    return this.http.patch<Task>(`${this.tasksUrl}/${id}/move`, { status, position });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.tasksUrl}/${id}`);
  }
}
