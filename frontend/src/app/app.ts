import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { SessionStore } from './core/auth/session.store';

/** Mise en page commune : en-tête (navigation selon le rôle), puis la page courante. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly session = inject(SessionStore);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  logout(): void {
    // subscribe obligatoire : un Observable HTTP ne part qu'à l'abonnement.
    // complete et error : dans les deux cas la session locale est déjà vidée (finalize).
    this.auth.logout().subscribe({
      complete: () => this.router.navigate(['/']),
      error: () => this.router.navigate(['/']),
    });
  }
}
