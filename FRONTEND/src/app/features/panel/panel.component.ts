import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ServerService } from '../../core/services/server.service';
import { StatusDotComponent } from '../../shared/status-dot/status-dot.component';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-panel',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule, StatusDotComponent, PageHeaderComponent],
  templateUrl: './panel.component.html',
  styleUrl: './panel.component.scss'
})
export class PanelComponent {
  readonly serverService = inject(ServerService);

  readonly quickCards = [
    { icon: 'people',  title: '👤  Clientes',   body: 'Gestione los clientes conectados al servidor.',   path: '/clientes' },
    { icon: 'folder',  title: '📁  Archivos',   body: 'Consulte los documentos almacenados.',            path: '/archivos' },
    { icon: 'list_alt',title: '☰  Logs',        body: 'Revise el registro de actividad técnica.',        path: '/logs' },
  ];
}
