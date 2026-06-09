import { Component, OnInit, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { ServerService } from '../../core/services/server.service';
import { Servidor } from '../../core/models';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [
    CommonModule, RouterLink, RouterLinkActive, FormsModule,
    MatSelectModule, MatFormFieldModule, MatListModule, MatIconModule
  ],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent implements OnInit {
  readonly serverService = inject(ServerService);

  readonly navItems = [
    { path: 'panel',      icon: 'dashboard',  label: 'Panel' },
    { path: 'clientes',   icon: 'people',     label: 'Clientes' },
    { path: 'mensajes',   icon: 'message',    label: 'Mensajes' },
    { path: 'archivos',   icon: 'folder',     label: 'Archivos' },
    { path: 'logs',       icon: 'list_alt',   label: 'Logs' },
    { path: 'servidores', icon: 'dns',        label: 'Servidores' },
    { path: 'ml',         icon: 'music_note', label: 'Clasificador ML' },
  ];

  ngOnInit(): void {
    this.serverService.cargarServidores().subscribe();
  }

  seleccionar(servidor: Servidor): void {
    this.serverService.seleccionar(servidor);
  }

  comparar(a: Servidor | null, b: Servidor | null): boolean {
    return a?.servidorId === b?.servidorId;
  }
}
