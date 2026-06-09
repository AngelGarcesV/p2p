import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell/shell.component';

export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      { path: '', redirectTo: 'panel', pathMatch: 'full' },
      {
        path: 'panel',
        loadComponent: () => import('./features/panel/panel.component').then(m => m.PanelComponent)
      },
      {
        path: 'clientes',
        loadComponent: () => import('./features/clientes/clientes.component').then(m => m.ClientesComponent)
      },
      {
        path: 'mensajes',
        loadComponent: () => import('./features/mensajes/mensajes.component').then(m => m.MensajesComponent)
      },
      {
        path: 'archivos',
        loadComponent: () => import('./features/archivos/archivos.component').then(m => m.ArchivosComponent)
      },
      {
        path: 'logs',
        loadComponent: () => import('./features/logs/logs.component').then(m => m.LogsComponent)
      },
      {
        path: 'servidores',
        loadComponent: () => import('./features/servidores/servidores.component').then(m => m.ServidoresComponent)
      },
      {
        path: 'ml',
        loadComponent: () => import('./features/ml/ml.component').then(m => m.MlComponent)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
