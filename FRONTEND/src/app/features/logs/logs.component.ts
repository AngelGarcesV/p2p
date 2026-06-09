import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ApiService } from '../../core/services/api.service';
import { ServerService } from '../../core/services/server.service';
import { LogServidor, Pagina } from '../../core/models';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';

@Component({
  selector: 'app-logs',
  standalone: true,
  imports: [
    CommonModule, MatTableModule, MatButtonModule, MatIconModule,
    MatProgressBarModule, MatPaginatorModule, PageHeaderComponent, EmptyStateComponent
  ],
  templateUrl: './logs.component.html',
  styleUrl: './logs.component.scss'
})
export class LogsComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly serverService = inject(ServerService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly datos = signal<LogServidor[]>([]);
  readonly total = signal(0);

  pagina = 0;
  tamanoPagina = 50;

  readonly columnas = ['nivel', 'mensaje', 'origen', 'ipRemitente', 'fechaEvento'];

  ngOnInit(): void { this.cargar(); }

  cargar(): void {
    const srv = this.serverService.seleccionado$();
    if (!srv) return;
    this.loading.set(true);
    this.error.set(null);
    this.api.get<Pagina<LogServidor>>(srv.servidorId, 'logs', {
      pagina: String(this.pagina),
      tamanoPagina: String(this.tamanoPagina)
    }).subscribe({
      next: page => {
        this.datos.set(page.datos);
        this.total.set(page.total);
        this.loading.set(false);
      },
      error: err => { this.error.set(err.message); this.loading.set(false); }
    });
  }

  onPage(event: PageEvent): void {
    this.pagina = event.pageIndex;
    this.tamanoPagina = event.pageSize;
    this.cargar();
  }

  nivelClass(nivel: string): string {
    return `log-${nivel.toUpperCase()}`;
  }
}
