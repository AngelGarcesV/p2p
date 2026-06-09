import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from '../sidebar/sidebar.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent],
  styles: [`
    :host { display: flex; height: 100vh; overflow: hidden; }
    main { flex: 1; overflow-y: auto; background: var(--bg-primary); }
  `],
  template: `
    <app-sidebar />
    <main><router-outlet /></main>
  `
})
export class ShellComponent {}
