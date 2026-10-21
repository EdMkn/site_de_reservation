import { Routes } from '@angular/router';
import { requireRole } from './core/auth/auth.guards';

// loadComponent : chaque page est un fichier JavaScript séparé, téléchargé à la première
// visite. La page d'accueil ne charge pas le code de la connexion, ni l'inverse.
export const routes: Routes = [
  { path: '', title: 'Trouver un praticien', loadComponent: () => import('./pages/home/home').then((m) => m.Home) },
  {
    path: 'praticiens/:id',
    title: 'Prendre rendez-vous',
    loadComponent: () => import('./pages/practitioner/practitioner').then((m) => m.PractitionerPage),
  },
  {
    path: 'reservation',
    title: 'Confirmer le rendez-vous',
    canActivate: [requireRole('PATIENT')],
    loadComponent: () => import('./pages/booking/booking').then((m) => m.BookingPage),
  },
  {
    path: 'mes-rendez-vous',
    title: 'Mes rendez-vous',
    canActivate: [requireRole('PATIENT')],
    loadComponent: () => import('./pages/my-appointments/my-appointments').then((m) => m.MyAppointmentsPage),
  },
  {
    path: 'agenda',
    title: 'Mon agenda',
    canActivate: [requireRole('PRATICIEN')],
    loadComponent: () => import('./pages/agenda/agenda').then((m) => m.AgendaPage),
  },
  { path: 'connexion', title: 'Connexion', loadComponent: () => import('./pages/login/login').then((m) => m.Login) },
  { path: '**', redirectTo: '' },
];
