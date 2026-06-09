import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatCardModule } from '@angular/material/card';
import { ApiService } from '../../core/services/api.service';
import { ServerService } from '../../core/services/server.service';
import { GenerosMl, MlHealth } from '../../core/models';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-ml',
  standalone: true,
  imports: [
    CommonModule, MatButtonModule, MatIconModule,
    MatProgressBarModule, MatCardModule, PageHeaderComponent
  ],
  templateUrl: './ml.component.html',
  styleUrl: './ml.component.scss'
})
export class MlComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly serverService = inject(ServerService);

  readonly loading  = signal(false);
  readonly error    = signal<string | null>(null);
  readonly health   = signal<MlHealth | null>(null);
  readonly generos  = signal<string[]>([]);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    const srv = this.serverService.seleccionado$();
    if (!srv) return;
    this.loading.set(true);
    this.error.set(null);

    this.api.get<MlHealth>(srv.servidorId, 'ml/health').subscribe({
      next: res => this.health.set(res),
      error: () => this.health.set(null)
    });

    this.api.get<GenerosMl>(srv.servidorId, 'ml/generos').subscribe({
      next: res => { this.generos.set(res.generos); this.loading.set(false); },
      error: err => { this.error.set(err.message); this.loading.set(false); }
    });
  }
}
