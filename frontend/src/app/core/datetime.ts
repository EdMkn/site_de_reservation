// Dates et heures, toujours dans le fuseau du cabinet (reservation.timezone côté API) :
// un patient qui consulte depuis l'étranger doit voir "10:00", l'heure à laquelle il doit
// être au cabinet, pas l'heure de son ordinateur.
//
// Deux représentations seulement :
// - un instant : chaîne ISO avec décalage, telle que renvoyée par l'API ;
// - un jour du calendrier : chaîne "2026-11-02" (DayKey), sans heure ni fuseau.

export const PRACTICE_ZONE = 'Europe/Paris';

/** Jour du calendrier "AAAA-MM-JJ". */
export type DayKey = string;

// en-CA formate les dates en AAAA-MM-JJ : exactement le format attendu par l'API.
const dayKeyFormat = new Intl.DateTimeFormat('en-CA', {
  timeZone: PRACTICE_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});
const timeFormat = new Intl.DateTimeFormat('fr-FR', { timeZone: PRACTICE_ZONE, hour: '2-digit', minute: '2-digit' });
const dateTimeFormat = new Intl.DateTimeFormat('fr-FR', {
  timeZone: PRACTICE_ZONE,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  hour: '2-digit',
  minute: '2-digit',
});
// Un DayKey est manipulé comme minuit UTC (voir parse) : on le formate donc en UTC.
const dayLabelFormat = new Intl.DateTimeFormat('fr-FR', {
  timeZone: 'UTC',
  weekday: 'short',
  day: 'numeric',
  month: 'short',
});

/** Jour du calendrier, au cabinet, d'un instant ISO (ou de maintenant). */
export function dayKeyOf(instant: string | Date = new Date()): DayKey {
  return dayKeyFormat.format(new Date(instant));
}

/** "10:00" */
export function formatTime(instant: string): string {
  return timeFormat.format(new Date(instant));
}

/** "lundi 2 novembre à 10:00" */
export function formatDateTime(instant: string): string {
  return dateTimeFormat.format(new Date(instant));
}

/** "lun. 2 nov." */
export function formatDay(day: DayKey): string {
  return dayLabelFormat.format(parse(day));
}

export function isDayKey(value: string | undefined): value is DayKey {
  return !!value && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(parse(value).getTime());
}

/**
 * Calculs sur les jours en UTC : aucun changement d'heure en UTC, donc ajouter 7 jours
 * donne toujours le même jour de la semaine (en heure locale, un jour peut durer 23 h).
 */
export function addDays(day: DayKey, days: number): DayKey {
  const d = parse(day);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** Lundi de la semaine contenant ce jour. */
export function mondayOf(day: DayKey): DayKey {
  const weekday = parse(day).getUTCDay(); // 0 = dimanche
  return addDays(day, weekday === 0 ? -6 : 1 - weekday);
}

function parse(day: DayKey): Date {
  return new Date(`${day}T00:00:00Z`);
}
