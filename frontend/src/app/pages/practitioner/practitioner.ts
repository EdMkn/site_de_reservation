import { Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { of, switchMap } from 'rxjs';
import { PractitionerDetail, Slot } from '../../core/api/models';
import { PractitionersService } from '../../core/api/practitioners.service';
import { DayKey, addDays, dayKeyOf, formatDay, formatTime, isDayKey, mondayOf } from '../../core/datetime';
import { LoadState, toLoadState } from '../../core/load-state';

/**
 * Fiche praticien : présentation, choix du motif, puis créneaux de la semaine.
 *
 * Comme pour l'annuaire, l'état de la page est dans l'URL :
 * /praticiens/3?motif=7&semaine=2026-11-02. Après une connexion demandée en cliquant sur
 * un créneau, le patient revient exactement sur la même semaine et le même motif.
 */
@Component({
  selector: 'app-practitioner',
  imports: [RouterLink],
  templateUrl: './practitioner.html',
  styleUrl: './practitioner.scss',
})
export class PractitionerPage {
  private readonly api = inject(PractitionersService);
  private readonly router = inject(Router);

  // Paramètres de route, injectés par withComponentInputBinding.
  readonly id = input.required<string>();
  readonly motif = input<string>();
  readonly semaine = input<string>();

  protected readonly formatDay = formatDay;
  protected readonly formatTime = formatTime;

  private readonly practitionerId = computed(() => Number(this.id()));

  protected readonly detail = toSignal(
    toObservable(this.practitionerId).pipe(switchMap((id) => this.api.get(id).pipe(toLoadState()))),
    { initialValue: { status: 'loading' } as LoadState<PractitionerDetail> },
  );

  /** Motif choisi ; choisi d'office s'il n'y en a qu'un. */
  protected readonly selectedType = computed(() => {
    const state = this.detail();
    if (state.status !== 'ok') return undefined;
    const types = state.data.consultationTypes;
    return types.find((t) => t.id === Number(this.motif())) ?? (types.length === 1 ? types[0] : undefined);
  });

  /** Les semaines passées n'ont aucun créneau : une URL qui en demande une est ramenée à celle-ci. */
  protected readonly currentMonday = mondayOf(dayKeyOf());

  protected readonly weekStart = computed(() => {
    const requested = this.semaine();
    if (!isDayKey(requested)) return this.currentMonday;
    const monday = mondayOf(requested);
    return monday > this.currentMonday ? monday : this.currentMonday; // AAAA-MM-JJ : l'ordre alphabétique est l'ordre chronologique
  });

  protected readonly days = computed(() => Array.from({ length: 7 }, (_, i) => addDays(this.weekStart(), i)));

  private readonly slotQuery = computed(() => {
    const type = this.selectedType();
    return type ? { typeId: type.id, from: this.weekStart(), to: addDays(this.weekStart(), 6) } : null;
  });

  protected readonly slots = toSignal(
    toObservable(this.slotQuery).pipe(
      switchMap((q) =>
        q ? this.api.availability(this.practitionerId(), q.typeId, q.from, q.to).pipe(toLoadState()) : of(null),
      ),
    ),
    { initialValue: null },
  );

  /** Créneaux rangés par jour du calendrier (au cabinet), pour l'affichage en colonnes. */
  protected readonly slotsByDay = computed(() => {
    const state = this.slots();
    const byDay = new Map<DayKey, Slot[]>();
    if (state?.status === 'ok') {
      for (const slot of state.data) {
        const day = dayKeyOf(slot.start);
        byDay.set(day, [...(byDay.get(day) ?? []), slot]);
      }
    }
    return byDay;
  });

  /** Nom du cabinet, affiché seulement si le praticien exerce à plusieurs endroits. */
  protected locationLabel(slot: Slot): string | null {
    const state = this.detail();
    if (state.status !== 'ok' || slot.modality === 'DISTANCIEL' || state.data.locations.length < 2) return null;
    return state.data.locations.find((l) => l.id === slot.locationId)?.city ?? null;
  }

  selectType(value: string): void {
    this.router.navigate([], { queryParams: { motif: value || null }, queryParamsHandling: 'merge' });
  }

  changeWeek(weeks: number): void {
    this.router.navigate([], {
      queryParams: { semaine: addDays(this.weekStart(), 7 * weeks) },
      queryParamsHandling: 'merge',
    });
  }

  /**
   * Vers la confirmation, qui exige d'être connecté en patient (guard) : sinon, passage
   * par la connexion, puis retour sur la confirmation avec les mêmes paramètres.
   * Le serveur revérifiera de toute façon que le créneau est encore libre.
   */
  book(slot: Slot): void {
    this.router.navigate(['/reservation'], {
      queryParams: {
        praticien: this.practitionerId(),
        motif: this.selectedType()!.id,
        debut: slot.start,
        modalite: slot.modality,
        lieu: slot.locationId,
      },
    });
  }
}
