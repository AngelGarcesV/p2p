import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-status-dot',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="status-dot"
          [class.online]="isOnline"
          [class.offline]="!isOnline"
          [attr.aria-label]="isOnline ? 'Conectado' : 'Desconectado'">
    </span>
  `,
  styles: [`
    .status-dot {
      display: inline-block;
      width: 10px; height: 10px;
      border-radius: 50%;

      &.online  { background: var(--status-on);  box-shadow: 0 0 6px var(--status-on); }
      &.offline { background: var(--status-off); }
    }
  `]
})
export class StatusDotComponent {
  @Input() estado = '';
  get isOnline(): boolean {
    return this.estado.toUpperCase() === 'CONECTADO';
  }
}
