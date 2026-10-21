import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../api/models';
import { SessionStore } from './session.store';

/**
 * Confort d'affichage, pas sécurité : un guard s'exécute dans le navigateur et se
 * contourne. La vraie barrière reste le serveur (401 / 403 de Spring Security).
 */
export function requireRole(role: Role): CanActivateFn {
  return (_route, state) => {
    const store = inject(SessionStore);
    const router = inject(Router);
    if (!store.isLoggedIn()) {
      // UrlTree : le routeur annule la navigation et redirige, sans afficher la page.
      return router.createUrlTree(['/connexion'], { queryParams: { returnUrl: state.url } });
    }
    return store.role() === role ? true : router.createUrlTree(['/']);
  };
}
