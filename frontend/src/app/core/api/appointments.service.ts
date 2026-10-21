import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Appointment, BookingRequest, BookingResponse } from './models';

/** Rendez-vous de l'utilisateur connecté : le jeton est ajouté par authInterceptor. */
@Injectable({ providedIn: 'root' })
export class AppointmentsService {
  private readonly http = inject(HttpClient);

  /**
   * Patient : ses rendez-vous ; praticien : son agenda. Le serveur choisit selon le jeton.
   * Avec une période (jours "2026-11-02", bornes incluses, 31 jours au plus), seulement
   * les rendez-vous qui commencent dans cette période.
   */
  mine(period?: { from: string; to: string }): Observable<Appointment[]> {
    const params = period ? new HttpParams().set('from', period.from).set('to', period.to) : undefined;
    return this.http.get<Appointment[]>('/api/appointments', { params });
  }

  get(id: number): Observable<Appointment> {
    return this.http.get<Appointment>(`/api/appointments/${id}`);
  }

  book(request: BookingRequest): Observable<BookingResponse> {
    return this.http.post<BookingResponse>('/api/appointments', request);
  }

  cancel(id: number): Observable<Appointment> {
    return this.http.post<Appointment>(`/api/appointments/${id}/cancel`, null);
  }
}
