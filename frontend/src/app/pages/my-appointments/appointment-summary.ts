import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Appointment, AppointmentStatus } from '../../core/api/models';
import { formatDateTime } from '../../core/datetime';

const STATUS_LABELS: Record<AppointmentStatus, string> = {
  CONFIRME: 'Confirmé',
  ANNULE_PATIENT: 'Annulé par vous',
  ANNULE_PRATICIEN: 'Annulé par le praticien',
  HONORE: 'Effectué',
  ABSENT: 'Absence',
};

/** Composant d'affichage pur : reçoit un rendez-vous, n'appelle aucun service. */
@Component({
  selector: 'app-appointment-summary',
  imports: [RouterLink],
  template: `
    @let a = appointment();
    <div class="head">
      <strong class="when">{{ formatDateTime(a.start) }}</strong>
      <span class="status" [attr.data-status]="a.status">{{ statusLabels[a.status] }}</span>
    </div>
    <div>
      <a [routerLink]="['/praticiens', a.practitionerId]">{{ a.practitionerName }}</a> — {{ a.consultationType }}
    </div>
    <div class="muted">{{ a.modality === 'DISTANCIEL' ? 'Téléconsultation' : a.locationName }}</div>
    @if (a.patientNote) {
      <div class="muted note">Note : {{ a.patientNote }}</div>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 0.3rem;
    }

    .head {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      gap: 1rem;
      flex-wrap: wrap;
    }

    .when::first-letter {
      text-transform: uppercase;
    }

    .status {
      font-size: 0.8rem;
      font-weight: 600;
      padding: 0.1rem 0.55rem;
      border-radius: 999px;
      background: var(--color-primary-soft);
      color: var(--color-primary);

      &[data-status='ANNULE_PATIENT'],
      &[data-status='ANNULE_PRATICIEN'],
      &[data-status='ABSENT'] {
        background: var(--color-danger-soft);
        color: var(--color-danger);
      }

      &[data-status='HONORE'] {
        background: var(--color-success-soft);
        color: var(--color-success);
      }
    }

    .note {
      font-size: 0.9rem;
    }
  `,
})
export class AppointmentSummary {
  readonly appointment = input.required<Appointment>();

  protected readonly formatDateTime = formatDateTime;
  protected readonly statusLabels = STATUS_LABELS;
}
