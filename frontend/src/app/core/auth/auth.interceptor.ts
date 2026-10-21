import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SessionStore } from './session.store';

const LOGIN_URL = '/api/auth/login';

/**
 * 1. Ajoute "Authorization: Bearer <jeton>" aux appels vers notre API, et à eux seuls :
 *    le jeton ne doit jamais partir vers un autre domaine.
 * 2. Sur un 401 (jeton expiré ou révoqué), vide la session et renvoie vers la connexion,
 *    avec l'adresse courante pour y revenir ensuite. Sauf pour la connexion elle-même,
 *    où un 401 signifie "mauvais identifiants" et s'affiche dans le formulaire.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(SessionStore);
  const router = inject(Router);

  const token = store.token();
  const isApi = req.url.startsWith('/api/');
  const authorized = token && isApi ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authorized).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && isApi && req.url !== LOGIN_URL) {
        store.clear();
        router.navigate(['/connexion'], { queryParams: { returnUrl: router.url } });
      }
      return throwError(() => error);
    }),
  );
};
