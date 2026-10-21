import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Page d'origine (?returnUrl=), posée par le guard ou l'intercepteur. */
  readonly returnUrl = input<string>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();

    this.auth.login(email, password).subscribe({
      next: () => this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
      error: (e: unknown) => {
        this.submitting.set(false);
        // Le serveur renvoie le même 401 pour un email inconnu et un mauvais mot de passe :
        // on ne révèle pas non plus ici lequel des deux est faux.
        this.error.set(
          e instanceof HttpErrorResponse && e.status === 401
            ? 'Email ou mot de passe incorrect.'
            : 'Connexion impossible pour le moment. Réessayez plus tard.',
        );
      },
    });
  }
}

/**
 * N'accepte qu'un chemin interne ("/praticiens/3"). Sans ce contrôle, un lien piégé
 * ".../connexion?returnUrl=https://site-pirate.fr" renverrait l'utilisateur, juste après
 * sa connexion, vers un faux site (redirection ouverte). "//site-pirate.fr" est aussi
 * une URL externe pour le navigateur, d'où le second test.
 */
function safeReturnUrl(url: string | undefined): string {
  return url && url.startsWith('/') && !url.startsWith('//') ? url : '/';
}
