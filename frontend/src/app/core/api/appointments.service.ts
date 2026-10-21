import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Appointment, BookingRequest, BookingResponse } from './models';

/** Rendez-vous de l'utilisateur connecté : le jeton est ajouté par authInterceptor. */
@Injectable({ providedIn: 'root' })
export class AppointmentsService {
  private readonly http = inject(HttpClient);

  /** Patient : ses rendez-vous ; praticien : son agenda. Le serveur choisit selon le jeton. */
  mine(): Observable<Appointment[]> {
    return this.http.get<Appointment[]>('/api/appointments');
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
