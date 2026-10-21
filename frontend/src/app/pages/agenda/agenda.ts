import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { switchMap } from 'rxjs';
import { AppointmentsService } from '../../core/api/appointments.service';
import { Appointment, CANCELLATION_NOT_ALLOWED } from '../../core/api/models';
import { DayKey, addDays, dayKeyOf, formatDateTime, formatDay, isDayKey, mondayOf } from '../../core/datetime';
import { LoadState, toLoadState } from '../../core/load-state';
import { AppointmentSummary } from '../../shared/appointment-summary';

/**
 * Agenda du praticien, semaine par semaine (?semaine=2026-11-02).
 *
 * Seule la semaine affichée est demandée au serveur (GET /api/appointments?from=&to=) :
 * la requête reste aussi rapide après des années d'historique.
 */
@Component({
  selector: 'app-agenda',
  imports: [AppointmentSummary],
  templateUrl: './agenda.html',
  styleUrl: './agenda.scss',
})
export class AgendaPage {
  private readonly api = inject(AppointmentsService);
  private readonly router = inject(Router);

  readonly semaine = input<string>();

  protected readonly formatDay = formatDay;
  protected readonly currentDayKey = dayKeyOf();
  protected readonly currentMonday = mondayOf(this.currentDayKey);

  /** Contrairement à la fiche praticien, les semaines passées restent consultables. */
  protected readonly weekStart = computed(() => {
    const requested = this.semaine();
    return isDayKey(requested) ? mondayOf(requested) : this.currentMonday;
  });

  protected readonly days = computed(() => Array.from({ length: 7 }, (_, i) => addDays(this.weekStart(), i)));

  private readonly loaded = toSignal(
    toObservable(this.weekStart).pipe(
      switchMap((monday) => this.api.mine({ from: monday, to: addDays(monday, 6) }).pipe(toLoadState())),
    ),
    { initialValue: { status: 'loading' } as LoadState<Appointment[]> },
  );

  /** Comme dans "Mes rendez-vous" : modifiable localement après une annulation. */
  protected readonly appointments = linkedSignal(() => this.loaded());

  /** Annulés masqués par défaut : ils libèrent le créneau, l'agenda reste lisible. */
  protected readonly showCancelled = signal(false);

  private readonly visible = computed(() => {
    const state = this.appointments();
    if (state.status !== 'ok') return [];
    return this.showCancelled() ? state.data : state.data.filter((a) => !isCancelled(a));
  });

  protected readonly cancelledCount = computed(() => {
    const state = this.appointments();
    return state.status === 'ok' ? state.data.filter(isCancelled).length : 0;
  });

  protected readonly byDay = computed(() => {
    const map = new Map<DayKey, Appointment[]>();
    for (const a of this.visible()) {
      const day = dayKeyOf(a.start);
      map.set(day, [...(map.get(day) ?? []), a]);
    }
    return map;
  });

  protected readonly cancelling = signal<number | null>(null);
  protected readonly cancelError = signal<{ id: number; message: string } | null>(null);

  /** Règle du praticien (Appointment.cancel côté serveur) : tant que le rendez-vous n'a pas commencé. */
  protected canCancel(a: Appointment): boolean {
    return a.status === 'CONFIRME' && Date.parse(a.start) > Date.now();
  }

  changeWeek(weeks: number): void {
    this.goToWeek(addDays(this.weekStart(), 7 * weeks));
  }

  goToWeek(monday: DayKey): void {
    // null pour la semaine en cours : l'URL reste /agenda, plus simple à partager.
    this.router.navigate([], { queryParams: { semaine: monday === this.currentMonday ? null : monday } });
  }

  cancel(a: Appointment): void {
    if (!confirm(`Annuler le rendez-vous de ${a.patientName} (${formatDateTime(a.start)}) ?`)) return;
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
            ? 'Ce rendez-vous a déjà commencé ou n\'est plus confirmé : il ne peut plus être annulé.'
            : "L'annulation a échoué. Réessayez plus tard.",
        });
      },
    });
  }
}

function isCancelled(a: Appointment): boolean {
  return a.status === 'ANNULE_PATIENT' || a.status === 'ANNULE_PRATICIEN';
}
