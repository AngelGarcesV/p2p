import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../../core/services/api.service';
import { ServerService } from '../../core/services/server.service';
import { Cliente } from '../../core/models';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';

@Component({
  selector: 'app-clientes',
  standalone: true,
  imports: [
    CommonModule, MatTableModule, MatButtonModule, MatIconModule,
    MatProgressBarModule, PageHeaderComponent, EmptyStateComponent
  ],
  templateUrl: './clientes.component.html',
  styleUrl: './clientes.component.scss'
})
export class ClientesComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly serverService = inject(ServerService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly datos = signal<Cliente[]>([]);

  readonly columnas = ['username', 'ip', 'puerto', 'protocolo', 'creadoEn', 'ultimoAcceso'];

  ngOnInit(): void { this.cargar(); }

  cargar(): void {
    const srv = this.serverService.seleccionado$();
    if (!srv) return;
    this.loading.set(true);
    this.error.set(null);
    this.api.get<Cliente[]>(srv.servidorId, 'clientes').subscribe({
      next: data => { this.datos.set(data); this.loading.set(false); },
      error: err  => { this.error.set(err.message); this.loading.set(false); }
    });
  }
}
