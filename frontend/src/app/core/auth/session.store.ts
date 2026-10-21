import { Injectable, computed, signal } from '@angular/core';
import { Role } from '../api/models';

export interface StoredSession {
  token: string;
  userId: number;
  role: Role;
}

const KEY = 'reservation.session';

/**
 * Session courante, en mémoire (signal) et dans localStorage pour survivre à un
 * rechargement de page.
 *
 * Volontairement sans HttpClient : authInterceptor en dépend, et HttpClient dépend des
 * intercepteurs. Mettre ici les appels HTTP créerait une dépendance circulaire.
 *
 * L'expiration n'est pas vérifiée côté client : le serveur la repousse à chaque requête
 * (expiration glissante), la date reçue à la connexion devient vite fausse. Le serveur
 * fait foi : un 401 vide la session (voir authInterceptor).
 */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly session = signal<StoredSession | null>(read());

  readonly current = this.session.asReadonly();
  readonly isLoggedIn = computed(() => this.session() !== null);
  readonly role = computed(() => this.session()?.role ?? null);
  readonly token = computed(() => this.session()?.token ?? null);

  save(session: StoredSession): void {
    this.session.set(session);
    try {
      localStorage.setItem(KEY, JSON.stringify(session));
    } catch {
      // Stockage indisponible (navigation privée, quota) : la session reste en mémoire.
    }
  }

  clear(): void {
    this.session.set(null);
    try {
      localStorage.removeItem(KEY);
    } catch {
      // idem
    }
  }
}

function read(): StoredSession | null {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as StoredSession) : null;
  } catch {
    return null;
  }
}
