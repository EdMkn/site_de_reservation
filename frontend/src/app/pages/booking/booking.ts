import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { AppointmentsService } from '../../core/api/appointments.service';
import { Modality, PATIENT_UNAVAILABLE, PractitionerDetail, SLOT_UNAVAILABLE } from '../../core/api/models';
import { PractitionersService } from '../../core/api/practitioners.service';
import { dayKeyOf, formatDateTime, mondayOf } from '../../core/datetime';
import { LoadState, toLoadState } from '../../core/load-state';

/** Même limite que @Size(max = 1000) sur BookingRequest.patientNote. */
const NOTE_MAX_LENGTH = 1000;

type Failure = 'slot-taken' | 'patient-busy' | 'other';

/**
 * Confirmation d'un créneau choisi sur la fiche praticien (réservée aux patients).
 *
 * Les paramètres arrivent par l'URL, donc peuvent être modifiés à la main : la page ne
 * s'en sert que pour afficher un récapitulatif, et c'est le serveur qui décide si le
 * créneau est réservable (même calcul que pour l'affichage des disponibilités).
 */
@Component({
  selector: 'app-booking',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './booking.html',
  styleUrl: './booking.scss',
})
export class BookingPage {
  private readonly practitioners = inject(PractitionersService);
  private readonly appointments = inject(AppointmentsService);
  private readonly router = inject(Router);

  // Paramètres posés par PractitionerPage.book().
  readonly praticien = input<string>();
  readonly motif = input<string>();
  readonly debut = input<string>();
  readonly modalite = input<string>();
  readonly lieu = input<string>();

  protected readonly noteMaxLength = NOTE_MAX_LENGTH;
  protected readonly note = new FormControl('', { nonNullable: true, validators: Validators.maxLength(NOTE_MAX_LENGTH) });
  protected readonly submitting = signal(false);
  protected readonly failure = signal<Failure | null>(null);

  private readonly practitionerId = computed(() => Number(this.praticien()));

  protected readonly detail = toSignal(
    toObservable(this.practitionerId).pipe(switchMap((id) => this.practitioners.get(id).pipe(toLoadState()))),
    { initialValue: { status: 'loading' } as LoadState<PractitionerDetail> },
  );

  /** Le récapitulatif, ou null si les paramètres ne correspondent pas à ce praticien. */
  protected readonly summary = computed(() => {
    const state = this.detail();
    const start = this.debut();
    const modality = this.modalite();
    if (state.status !== 'ok' || !start || Number.isNaN(Date.parse(start))) return null;
    if (modality !== 'PRESENTIEL' && modality !== 'DISTANCIEL') return null;

    const p = state.data;
    const type = p.consultationTypes.find((t) => t.id === Number(this.motif()));
    const location = modality === 'PRESENTIEL' ? p.locations.find((l) => l.id === Number(this.lieu())) : null;
    if (!type || location === undefined || (modality === 'DISTANCIEL' && !type.remoteAllowed)) return null;

    return {
      practitioner: p,
      type,
      start,
      modality: modality as Modality,
      location,
      when: formatDateTime(start),
    };
  });

  /** Retour à la fiche, sur la semaine du créneau : les disponibilités y sont rechargées. */
  protected readonly backToSlots = computed(() => ({
    path: ['/praticiens', this.practitionerId()],
    query: { motif: this.motif(), semaine: this.debut() ? mondayOf(dayKeyOf(this.debut()!)) : null },
  }));

  confirm(): void {
    const s = this.summary();
    if (!s || this.note.invalid) return;
    this.submitting.set(true);
    this.failure.set(null);

    this.appointments
      .book({
        practitionerId: s.practitioner.id,
        consultationTypeId: s.type.id,
        modality: s.modality,
        locationId: s.location?.id ?? null,
        // La chaîne reçue de l'API, renvoyée telle quelle : aucune conversion, donc aucun
        // risque de décaler le créneau d'une heure.
        start: s.start,
        patientNote: this.note.value.trim() || null,
      })
      .subscribe({
        next: (res) => this.router.navigate(['/mes-rendez-vous'], { queryParams: { nouveau: res.appointmentId } }),
        error: (e: unknown) => {
          this.submitting.set(false);
          this.failure.set(failureOf(e));
        },
      });
  }
}

/** On s'appuie sur le "type" du ProblemDetail, stable, plutôt que sur le texte du message. */
function failureOf(e: unknown): Failure {
  if (e instanceof HttpErrorResponse && e.status === 409) {
    if (e.error?.type === SLOT_UNAVAILABLE) return 'slot-taken';
    if (e.error?.type === PATIENT_UNAVAILABLE) return 'patient-busy';
  }
  return 'other';
}
