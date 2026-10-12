package com.emakon.reservation.availability;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Calcule les créneaux libres d'un praticien (section 6 du modèle de données).
 *
 * Sans état et sans dépendance à Spring ou JPA : on lui donne des données simples,
 * il renvoie des créneaux. Le service l'instanciera (ou le déclarera en bean) avec
 * l'horloge et le fuseau de l'application.
 */
public class AvailabilityCalculator {

    private static final Comparator<Slot> CHRONOLOGICAL =
            Comparator.comparing(Slot::start).thenComparing(Slot::modality);

    private final Clock clock;
    private final ZoneId zone;

    /**
     * @param clock source de "maintenant" ; injectée pour pouvoir la figer dans les tests
     * @param zone  fuseau des cabinets : les plages horaires sont exprimées dans ce fuseau
     */
    public AvailabilityCalculator(Clock clock, ZoneId zone) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public List<Slot> compute(PractitionerAgenda agenda, AvailabilityQuery query) {
        Instant now = clock.instant();
        List<TimeRange> busy = new ArrayList<>(agenda.absences());
        busy.addAll(agenda.appointments());

        return query.from().datesUntil(query.to().plusDays(1))      // chaque jour de la période (to inclus)
                .flatMap(date -> agenda.rules().stream()
                        .filter(rule -> rule.appliesTo(date))
                        .filter(rule -> isCompatible(rule, query))
                        .flatMap(rule -> split(rule, date, query).stream()))
                .filter(slot -> !slot.start().isBefore(now))         // créneaux passés
                .filter(slot -> isFree(slot, busy))                  // absences et rendez-vous
                .distinct()                                          // deux règles qui se recouvrent
                .sorted(CHRONOLOGICAL)
                .toList();
    }

    /** Une plage en distanciel n'est proposée que si le motif autorise la téléconsultation. */
    private static boolean isCompatible(ScheduleRule rule, AvailabilityQuery query) {
        return rule.modality() == Modality.PRESENTIEL || query.remoteAllowed();
    }

    /**
     * Découpe la plage de ce jour en créneaux consécutifs de la durée du motif.
     * Un reste trop court pour un créneau complet est abandonné (ex. 9h–12h en 40 min
     * donne 9:00, 9:40, 10:20 et 11:00 ; les 20 dernières minutes ne sont pas proposées).
     *
     * Le calcul se fait en Instant, pas en LocalTime : ZonedDateTime.of applique le bon
     * décalage UTC pour chaque date (heure d'été / d'hiver), et la boucle ne peut pas
     * "repasser" par minuit comme le ferait une addition sur LocalTime.
     */
    private List<Slot> split(ScheduleRule rule, LocalDate date, AvailabilityQuery query) {
        Instant rangeStart = ZonedDateTime.of(date, rule.start(), zone).toInstant();
        Instant rangeEnd = ZonedDateTime.of(date, rule.end(), zone).toInstant();

        List<Slot> slots = new ArrayList<>();
        Instant start = rangeStart;
        Instant end = start.plus(query.duration());
        while (!end.isAfter(rangeEnd)) {
            slots.add(new Slot(start, end, rule.modality(), rule.locationId()));
            start = end;
            end = start.plus(query.duration());
        }
        return slots;
    }

    /**
     * Complexité : O(créneaux × occupations). Suffisant pour une période de quelques
     * semaines ; au-delà, on trierait les occupations pour les parcourir en un seul passage.
     */
    private static boolean isFree(Slot slot, List<TimeRange> busy) {
        TimeRange range = slot.range();
        return busy.stream().noneMatch(range::overlaps);
    }
}
