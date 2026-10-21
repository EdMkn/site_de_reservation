import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, finalize, map } from 'rxjs';
import { LoginResponse, Role } from '../api/models';
import { SessionStore } from './session.store';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly store = inject(SessionStore);

  /** Erreur 401 si les identifiants sont refusés : à afficher par la page de connexion. */
  login(email: string, password: string): Observable<Role> {
    return this.http.post<LoginResponse>('/api/auth/login', { email, password }).pipe(
      map((res) => {
        this.store.save({ token: res.token, userId: res.userId, role: res.role });
        return res.role;
      }),
    );
  }

  /**
   * Détruit la session côté serveur, puis côté client même si l'appel échoue (réseau,
   * jeton déjà expiré) : l'utilisateur qui clique sur "Se déconnecter" doit l'être.
   */
  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', null).pipe(finalize(() => this.store.clear()));
  }
}
