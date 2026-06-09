import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-page-header',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="view-header">
      <div class="header-text">
        <h1 class="view-title">{{ titulo }}</h1>
        @if (subtitulo) {
          <p class="view-subtitle">{{ subtitulo }}</p>
        }
      </div>
      <div class="header-actions">
        <ng-content />
      </div>
    </div>
  `
})
export class PageHeaderComponent {
  @Input({ required: true }) titulo = '';
  @Input() subtitulo = '';
}
