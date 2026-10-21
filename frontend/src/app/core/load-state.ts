import { Observable, OperatorFunction, catchError, map, of, startWith } from 'rxjs';

/**
 * État d'un chargement, pour que le template affiche toujours quelque chose de juste :
 * un indicateur, les données ou une erreur, jamais une liste vide ambiguë.
 */
export type LoadState<T> =
  | { status: 'loading' }
  | { status: 'ok'; data: T }
  | { status: 'error'; error: unknown };

/** Transforme un appel HTTP en flux d'états : 'loading' tout de suite, puis 'ok' ou 'error'. */
export function toLoadState<T>(): OperatorFunction<T, LoadState<T>> {
  return (source: Observable<T>) =>
    source.pipe(
      map((data): LoadState<T> => ({ status: 'ok', data })),
      catchError((error: unknown) => of<LoadState<T>>({ status: 'error', error })),
      startWith<LoadState<T>>({ status: 'loading' }),
    );
}
