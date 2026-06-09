import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../../core/services/api.service';
import { ServerService } from '../../core/services/server.service';
import { Archivo } from '../../core/models';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';

@Component({
  selector: 'app-archivos',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatTableModule, MatButtonModule, MatIconModule,
    MatInputModule, MatFormFieldModule, MatProgressBarModule,
    PageHeaderComponent, EmptyStateComponent
  ],
  templateUrl: './archivos.component.html',
  styleUrl: './archivos.component.scss'
})
export class ArchivosComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly serverService = inject(ServerService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly datos = signal<Archivo[]>([]);
  filtroUsername = '';

  readonly columnas = ['nombreArchivo', 'extension', 'remitente', 'destinatario', 'tamano', 'fechaRecepcion', 'servidorOrigen', 'hashSha256'];

  ngOnInit(): void { this.cargar(); }

  cargar(): void {
    const srv = this.serverService.seleccionado$();
    if (!srv) return;
    this.loading.set(true);
    this.error.set(null);
    const params = this.filtroUsername.trim()
      ? { username: this.filtroUsername.trim() }
      : undefined;
    this.api.get<Archivo[]>(srv.servidorId, 'archivos', params).subscribe({
      next: data => { this.datos.set(data); this.loading.set(false); },
      error: err  => { this.error.set(err.message); this.loading.set(false); }
    });
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1048576).toFixed(1)} MB`;
  }
}
