import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Appointment, AppointmentStatus } from '../core/api/models';
import { formatDateTime, formatTime } from '../core/datetime';

/** Qui regarde : les libellés et l'interlocuteur affiché en dépendent. */
export type Viewpoint = 'patient' | 'practitioner';

const STATUS_LABELS: Record<Viewpoint, Record<AppointmentStatus, string>> = {
  patient: {
    CONFIRME: 'Confirmé',
    ANNULE_PATIENT: 'Annulé par vous',
    ANNULE_PRATICIEN: 'Annulé par le praticien',
    HONORE: 'Effectué',
    ABSENT: 'Absence',
  },
  practitioner: {
    CONFIRME: 'Confirmé',
    ANNULE_PATIENT: 'Annulé par le patient',
    ANNULE_PRATICIEN: 'Annulé par vous',
    HONORE: 'Effectué',
    ABSENT: 'Patient absent',
  },
};

/** Composant d'affichage pur : reçoit un rendez-vous, n'appelle aucun service. */
@Component({
  selector: 'app-appointment-summary',
  imports: [RouterLink],
  template: `
    @let a = appointment();
    <div class="head">
      @if (viewpoint() === 'patient') {
        <strong class="when">{{ formatDateTime(a.start) }}</strong>
      } @else {
        <!-- Dans l'agenda, le jour est déjà en titre de colonne : l'horaire suffit. -->
        <strong>{{ formatTime(a.start) }} – {{ formatTime(a.end) }}</strong>
      }
      <span class="status" [attr.data-status]="a.status">{{ statusLabels()[a.status] }}</span>
    </div>
    <div>
      @if (viewpoint() === 'patient') {
        <a [routerLink]="['/praticiens', a.practitionerId]">{{ a.practitionerName }}</a>
      } @else {
        <strong>{{ a.patientName }}</strong>
      }
      — {{ a.consultationType }}
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
  readonly viewpoint = input<Viewpoint>('patient');

  protected readonly formatDateTime = formatDateTime;
  protected readonly formatTime = formatTime;
  protected readonly statusLabels = computed(() => STATUS_LABELS[this.viewpoint()]);
}
