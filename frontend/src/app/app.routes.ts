import { Routes } from '@angular/router';

// loadComponent : chaque page est un fichier JavaScript séparé, téléchargé à la première
// visite. La page d'accueil ne charge pas le code de la connexion, ni l'inverse.
export const routes: Routes = [
  { path: '', title: 'Trouver un praticien', loadComponent: () => import('./pages/home/home').then((m) => m.Home) },
  { path: 'connexion', title: 'Connexion', loadComponent: () => import('./pages/login/login').then((m) => m.Login) },
  { path: '**', redirectTo: '' },
];
