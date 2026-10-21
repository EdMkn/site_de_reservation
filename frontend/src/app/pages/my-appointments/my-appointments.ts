import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { AppointmentsService } from '../../core/api/appointments.service';
import { Appointment, CANCELLATION_NOT_ALLOWED } from '../../core/api/models';
import { formatDateTime } from '../../core/datetime';
import { LoadState, toLoadState } from '../../core/load-state';
import { AppointmentSummary } from '../../shared/appointment-summary';

/** Même délai que Appointment.PATIENT_CANCELLATION_NOTICE côté serveur. */
const CANCELLATION_NOTICE_MS = 24 * 60 * 60 * 1000;

@Component({
  selector: 'app-my-appointments',
  imports: [RouterLink, AppointmentSummary],
  templateUrl: './my-appointments.html',
  styleUrl: './my-appointments.scss',
})
export class MyAppointmentsPage {
  private readonly api = inject(AppointmentsService);

  /** ?nouveau=<id> : posé par la page de confirmation après une réservation réussie. */
  readonly nouveau = input<string>();

  protected readonly formatDateTime = formatDateTime;

  // Heure de référence pour "à venir" et "annulable", fixée au chargement de la page.
  private readonly now = Date.now();

  private readonly loaded = toSignal(this.api.mine().pipe(toLoadState()), {
    initialValue: { status: 'loading' } as LoadState<Appointment[]>,
  });

  /**
   * linkedSignal : suit le chargement (comme computed) mais reste modifiable. Après une
   * annulation, on y remplace le rendez-vous par celui renvoyé par le serveur, sans
   * recharger toute la liste.
   */
  protected readonly appointments = linkedSignal(() => this.loaded());

  protected readonly upcoming = computed(() =>
    this.list().filter((a) => a.status === 'CONFIRME' && Date.parse(a.end) > this.now),
  );

  /** Passés ou annulés, du plus récent au plus ancien. */
  protected readonly history = computed(() =>
    this.list()
      .filter((a) => !(a.status === 'CONFIRME' && Date.parse(a.end) > this.now))
      .reverse(),
  );

  protected readonly justBooked = computed(() => {
    const id = Number(this.nouveau());
    return this.upcoming().find((a) => a.id === id) ?? null;
  });

  protected readonly cancelling = signal<number | null>(null);
  protected readonly cancelError = signal<{ id: number; message: string } | null>(null);

  /**
   * Confort d'affichage : le bouton est grisé à moins de 24 h. Le serveur applique la même
   * règle avec sa propre horloge, et refuse (409) si l'horloge du navigateur est fausse.
   */
  protected canCancel(a: Appointment): boolean {
    return a.status === 'CONFIRME' && Date.parse(a.start) - this.now >= CANCELLATION_NOTICE_MS;
  }

  cancel(a: Appointment): void {
    if (!confirm(`Annuler le rendez-vous du ${formatDateTime(a.start)} avec ${a.practitionerName} ?`)) return;
    this.cancelling.set(a.id);
    this.cancelError.set(null);

    this.api.cancel(a.id).subscribe({
      next: (updated) => {
        this.cancelling.set(null);
        this.appointments.update((state) =>
          state.status === 'ok'
            ? { ...state, data: state.data.map((x) => (x.id === updated.id ? updated : x)) }
            : state,
        );
      },
      error: (e: unknown) => {
        this.cancelling.set(null);
        const notAllowed = e instanceof HttpErrorResponse && e.status === 409 && e.error?.type === CANCELLATION_NOT_ALLOWED;
        this.cancelError.set({
          id: a.id,
          message: notAllowed
            ? "Ce rendez-vous ne peut plus être annulé en ligne. Contactez le cabinet."
            : "L'annulation a échoué. Réessayez plus tard.",
        });
      },
    });
  }

  private list(): Appointment[] {
    const state = this.appointments();
    return state.status === 'ok' ? state.data : [];
  }
}
