import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PractitionerDetail, PractitionerSummary, Slot, Specialty } from './models';

/** Annuaire et disponibilités : endpoints publics, aucun jeton requis. */
@Injectable({ providedIn: 'root' })
export class PractitionersService {
  private readonly http = inject(HttpClient);

  specialties(): Observable<Specialty[]> {
    return this.http.get<Specialty[]>('/api/specialties');
  }

  list(specialtyId?: number): Observable<PractitionerSummary[]> {
    const params = specialtyId == null ? undefined : new HttpParams().set('specialtyId', specialtyId);
    return this.http.get<PractitionerSummary[]>('/api/practitioners', { params });
  }

  get(id: number): Observable<PractitionerDetail> {
    return this.http.get<PractitionerDetail>(`/api/practitioners/${id}`);
  }

  /** from et to au format "2026-11-02", bornes incluses (jours dans le fuseau du cabinet). */
  availability(practitionerId: number, consultationTypeId: number, from: string, to: string): Observable<Slot[]> {
    const params = new HttpParams().set('consultationTypeId', consultationTypeId).set('from', from).set('to', to);
    return this.http.get<Slot[]>(`/api/practitioners/${practitionerId}/availability`, { params });
  }
}
