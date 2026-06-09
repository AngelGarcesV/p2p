import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Servidor } from '../models';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ServerService {
  private readonly http = inject(HttpClient);
  private readonly gatewayUrl = environment.gatewayUrl;

  private readonly _servidores = signal<Servidor[]>([]);
  private readonly _seleccionado = signal<Servidor | null>(null);

  readonly servidores$ = this._servidores.asReadonly();
  readonly seleccionado$ = this._seleccionado.asReadonly();

  cargarServidores(): Observable<Servidor[]> {
    return this.http.get<Servidor[]>(`${this.gatewayUrl}/gateway/servidores`).pipe(
      tap(lista => {
        this._servidores.set(lista);
        if (lista.length > 0 && this._seleccionado() === null)
          this._seleccionado.set(lista[0]);
      })
    );
  }

  seleccionar(servidor: Servidor): void {
    this._seleccionado.set(servidor);
  }
}
