import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Page } from '../models/page.model';
import { Project, ProjectQuery, ProjectRequest, UpdateProjectRequest } from '../models/project.model';

/** `/api/projects/**` (docs/06). Yetki backend'de; arayüz `canManage`'e göre düğme gösterir/gizler. */
@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly projectsUrl = `${environment.apiUrl}/projects`;

  constructor(private readonly http: HttpClient) {}

  /** USER: üyesi olduğu projeler; ADMIN: tümü. Son güncellenen önce. */
  list(query: ProjectQuery): Observable<Page<Project>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.q.trim()) {
      params = params.set('q', query.q.trim());
    }
    if (query.status) {
      params = params.set('status', query.status);
    }
    return this.http.get<Page<Project>>(this.projectsUrl, { params });
  }

  get(id: number): Observable<Project> {
    return this.http.get<Project>(`${this.projectsUrl}/${id}`);
  }

  create(request: ProjectRequest): Observable<Project> {
    return this.http.post<Project>(this.projectsUrl, request);
  }

  update(id: number, request: UpdateProjectRequest): Observable<Project> {
    return this.http.put<Project>(`${this.projectsUrl}/${id}`, request);
  }

  /** Arşivler (yumuşak silme, 204). Geri almak: {@link restore}. */
  archive(id: number): Observable<void> {
    return this.http.delete<void>(`${this.projectsUrl}/${id}`);
  }

  /** Arşivden çıkarır. PUT tam güncelleme olduğu için ad ve açıklama da aynen gönderilir. */
  restore(project: Pick<Project, 'id' | 'name' | 'description'>): Observable<Project> {
    return this.update(project.id, { name: project.name, description: project.description, status: 'ACTIVE' });
  }
}
