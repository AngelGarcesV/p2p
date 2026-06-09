import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.gatewayUrl;

  get<T>(servidorId: string, path: string, params?: Record<string, string>): Observable<T> {
    const url = `${this.base}/gateway/${servidorId}/api/${path}`;
    const httpParams = params
      ? new HttpParams({ fromObject: params })
      : undefined;
    return this.http.get<T>(url, { params: httpParams });
  }

}
