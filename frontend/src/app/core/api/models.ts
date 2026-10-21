// Miroir des DTO de l'API (records Java du package api/ et service/PractitionerViews).
// Les dates arrivent en ISO 8601 avec décalage ("2026-11-02T10:00:00+01:00") : on les
// garde en chaîne, et on ne les convertit qu'à l'affichage.

export type Role = 'PATIENT' | 'PRATICIEN';
export type Modality = 'PRESENTIEL' | 'DISTANCIEL';
export type AppointmentStatus = 'CONFIRME' | 'ANNULE_PATIENT' | 'ANNULE_PRATICIEN' | 'HONORE' | 'ABSENT';

export interface Specialty {
  id: number;
  label: string;
}

export interface Location {
  id: number;
  name: string;
  address: string;
  postalCode: string;
  city: string;
}

export interface ConsultationType {
  id: number;
  label: string;
  durationMinutes: number;
  remoteAllowed: boolean;
}

export interface PractitionerSummary {
  id: number;
  firstName: string;
  lastName: string;
  specialties: Specialty[];
  cities: string[];
}

export interface PractitionerDetail {
  id: number;
  firstName: string;
  lastName: string;
  presentation: string | null;
  specialties: Specialty[];
  consultationTypes: ConsultationType[];
  locations: Location[];
}

export interface Slot {
  start: string;
  end: string;
  modality: Modality;
  locationId: number | null;
}

export interface BookingRequest {
  practitionerId: number;
  consultationTypeId: number;
  modality: Modality;
  locationId: number | null;
  start: string;
  patientNote: string | null;
}

export interface BookingResponse {
  appointmentId: number;
}

export interface Appointment {
  id: number;
  start: string;
  end: string;
  status: AppointmentStatus;
  modality: Modality;
  consultationType: string;
  practitionerId: number;
  practitionerName: string;
  patientId: number;
  patientName: string;
  locationId: number | null;
  locationName: string | null;
  patientNote: string | null;
  cancelledAt: string | null;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  userId: number;
  role: Role;
}

/** Corps des erreurs (ProblemDetail, RFC 9457) renvoyé par ApiExceptionHandler. */
export interface Problem {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
}

// Valeurs de "type" des 409, à comparer plutôt que le texte du message.
export const SLOT_UNAVAILABLE = '/problems/slot-unavailable';
export const PATIENT_UNAVAILABLE = '/problems/patient-unavailable';
export const CANCELLATION_NOT_ALLOWED = '/problems/cancellation-not-allowed';
