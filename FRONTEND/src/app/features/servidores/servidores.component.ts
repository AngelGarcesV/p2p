import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ServerService } from '../../core/services/server.service';
import { Servidor } from '../../core/models';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { StatusDotComponent } from '../../shared/status-dot/status-dot.component';

@Component({
  selector: 'app-servidores',
  standalone: true,
  imports: [
    CommonModule, MatTableModule, MatButtonModule, MatIconModule,
    MatProgressBarModule, PageHeaderComponent, EmptyStateComponent, StatusDotComponent
  ],
  templateUrl: './servidores.component.html',
  styleUrl: './servidores.component.scss'
})
export class ServidoresComponent {
  readonly serverService = inject(ServerService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly datos = signal<Servidor[]>([]);

  readonly columnas = ['estado', 'servidorId', 'host', 'puerto', 'intentosReconexion', 'ultimaConexion'];

  cargar(): void {
    this.loading.set(true);
    this.error.set(null);
    this.serverService.cargarServidores().subscribe({
      next: lista => { this.datos.set(lista); this.loading.set(false); },
      error: err   => { this.error.set(err.message); this.loading.set(false); }
    });
  }
}
