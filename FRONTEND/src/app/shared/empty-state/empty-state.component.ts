import { Component, Input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [MatIconModule],
  styles: [`
    :host {
      display: flex; flex-direction: column;
      align-items: center; justify-content: center;
      padding: 48px 24px; gap: 12px; color: var(--text-muted);
    }
    mat-icon { font-size: 48px; width: 48px; height: 48px; opacity: 0.4; }
    p { font-size: 14px; }
  `],
  template: `
    <mat-icon>{{ icono }}</mat-icon>
    <p>{{ mensaje }}</p>
  `
})
export class EmptyStateComponent {
  @Input() mensaje = 'Sin datos';
  @Input() icono = 'inbox';
}
