import { Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { PractitionersService } from '../../core/api/practitioners.service';
import { toLoadState } from '../../core/load-state';

/**
 * Accueil : l'annuaire des praticiens, filtrable par spécialité.
 *
 * Le filtre vit dans l'URL (?specialite=3) et non dans une variable du composant :
 * l'adresse se partage, se recharge, et le bouton "Précédent" annule un changement.
 */
@Component({
  selector: 'app-home',
  imports: [RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly api = inject(PractitionersService);
  private readonly router = inject(Router);

  /** Paramètre de requête ?specialite=, injecté par withComponentInputBinding. */
  readonly specialite = input<string>();

  protected readonly specialtyId = computed(() => {
    const id = Number(this.specialite());
    return Number.isInteger(id) && id > 0 ? id : undefined;
  });

  protected readonly specialties = toSignal(this.api.specialties(), { initialValue: [] });

  // switchMap : si le filtre change avant la réponse, la requête précédente est annulée,
  // et une réponse lente ne peut pas écraser le résultat du filtre le plus récent.
  protected readonly practitioners = toSignal(
    toObservable(this.specialtyId).pipe(switchMap((id) => this.api.list(id).pipe(toLoadState()))),
    { initialValue: { status: 'loading' } as const },
  );

  selectSpecialty(value: string): void {
    // null retire le paramètre de l'URL ; merge garde les éventuels autres paramètres.
    this.router.navigate([], {
      queryParams: { specialite: value || null },
      queryParamsHandling: 'merge',
    });
  }
}
