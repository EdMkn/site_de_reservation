package com.emakon.reservation.availability;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AvailabilityCalculatorTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final LocalDate MONDAY_NOV_2 = LocalDate.of(2026, 11, 2);
    private static final Long CABINET = 1L;

    /** Horloge figée bien avant les dates testées : aucun créneau n'est "passé", sauf dans les tests dédiés. */
    private final AvailabilityCalculator calculator = calculatorAt(LocalDateTime.of(2026, 10, 1, 0, 0));

    // ---------- Helpers : construire les entrées et lire les sorties en heure de Paris ----------

    private static AvailabilityCalculator calculatorAt(LocalDateTime nowInParis) {
        return new AvailabilityCalculator(Clock.fixed(paris(nowInParis), PARIS), PARIS);
    }

    private static Instant paris(LocalDateTime dateTime) {
        return dateTime.atZone(PARIS).toInstant();
    }

    private static Instant paris(LocalDate date, String time) {
        return paris(date.atTime(LocalTime.parse(time)));
    }

    private static ScheduleRule onSite(DayOfWeek day, String start, String end) {
        return new ScheduleRule(day, LocalTime.parse(start), LocalTime.parse(end),
                Modality.PRESENTIEL, CABINET, LocalDate.of(2026, 1, 1), null);
    }

    private static ScheduleRule remote(DayOfWeek day, String start, String end) {
        return new ScheduleRule(day, LocalTime.parse(start), LocalTime.parse(end),
                Modality.DISTANCIEL, null, LocalDate.of(2026, 1, 1), null);
    }

    private static PractitionerAgenda agenda(List<ScheduleRule> rules) {
        return new PractitionerAgenda(rules, List.of(), List.of());
    }

    private static AvailabilityQuery query(LocalDate from, LocalDate to, int minutes, boolean remoteAllowed) {
        return new AvailabilityQuery(from, to, Duration.ofMinutes(minutes), remoteAllowed);
    }

    private static AvailabilityQuery oneDay(LocalDate date, int minutes) {
        return query(date, date, minutes, false);
    }

    /** Débuts des créneaux, en heure locale de Paris, pour des assertions lisibles. */
    private static List<LocalDateTime> starts(List<Slot> slots) {
        return slots.stream().map(s -> LocalDateTime.ofInstant(s.start(), PARIS)).toList();
    }

    private static List<LocalDateTime> at(LocalDate date, String... times) {
        return java.util.Arrays.stream(times).map(t -> date.atTime(LocalTime.parse(t))).toList();
    }

    // ---------- Découpage ----------

    @Nested
    @DisplayName("Découpage des plages")
    class Splitting {

        @Test
        @DisplayName("une plage est découpée en créneaux consécutifs de la durée du motif")
        void splitsIntoConsecutiveSlots() {
            var slots = calculator.compute(agenda(List.of(onSite(MONDAY, "09:00", "12:00"))), oneDay(MONDAY_NOV_2, 30));

            assertThat(starts(slots)).containsExactlyElementsOf(
                    at(MONDAY_NOV_2, "09:00", "09:30", "10:00", "10:30", "11:00", "11:30"));
            assertThat(slots.getFirst().end()).isEqualTo(paris(MONDAY_NOV_2, "09:30"));
            assertThat(slots).allSatisfy(s -> {
                assertThat(s.modality()).isEqualTo(Modality.PRESENTIEL);
                assertThat(s.locationId()).isEqualTo(CABINET);
            });
        }

        @Test
        @DisplayName("un reste trop court pour un créneau complet n'est pas proposé")
        void dropsIncompleteRemainder() {
            var slots = calculator.compute(agenda(List.of(onSite(MONDAY, "09:00", "12:00"))), oneDay(MONDAY_NOV_2, 40));

            assertThat(starts(slots)).containsExactlyElementsOf(at(MONDAY_NOV_2, "09:00", "09:40", "10:20", "11:00"));
        }

        @Test
        @DisplayName("un motif plus long que la plage ne donne aucun créneau")
        void durationLongerThanRule() {
            var slots = calculator.compute(agenda(List.of(onSite(MONDAY, "09:00", "09:30"))), oneDay(MONDAY_NOV_2, 45));

            assertThat(slots).isEmpty();
        }
    }

    // ---------- Sélection des règles ----------

    @Nested
    @DisplayName("Sélection des plages applicables")
    class RuleSelection {

        @Test
        @DisplayName("une règle ne s'applique que le jour de la semaine prévu")
        void onlyOnRuleDay() {
            var week = query(MONDAY_NOV_2, MONDAY_NOV_2.plusDays(6), 60, false);

            var slots = calculator.compute(agenda(List.of(onSite(TUESDAY, "09:00", "11:00"))), week);

            assertThat(starts(slots)).containsExactlyElementsOf(at(MONDAY_NOV_2.plusDays(1), "09:00", "10:00"));
        }

        @Test
        @DisplayName("la période de validité est respectée, bornes incluses")
        void validityPeriod() {
            var rule = new ScheduleRule(MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), Modality.PRESENTIEL, CABINET,
                    LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16));
            var november = query(MONDAY_NOV_2, LocalDate.of(2026, 11, 30), 60, false);

            var slots = calculator.compute(agenda(List.of(rule)), november);

            assertThat(starts(slots)).containsExactly(
                    LocalDateTime.of(2026, 11, 9, 9, 0),
                    LocalDateTime.of(2026, 11, 16, 9, 0));
        }

        @Test
        @DisplayName("une plage en distanciel n'est proposée que si le motif l'autorise")
        void remoteRuleNeedsRemoteMotif() {
            var rules = agenda(List.of(remote(MONDAY, "09:00", "10:00")));

            assertThat(calculator.compute(rules, query(MONDAY_NOV_2, MONDAY_NOV_2, 30, false))).isEmpty();
            assertThat(calculator.compute(rules, query(MONDAY_NOV_2, MONDAY_NOV_2, 30, true)))
                    .hasSize(2)
                    .allSatisfy(s -> {
                        assertThat(s.modality()).isEqualTo(Modality.DISTANCIEL);
                        assertThat(s.locationId()).isNull();
                    });
        }
    }

    // ---------- Occupations ----------

    @Nested
    @DisplayName("Retrait des créneaux occupés")
    class Busy {

        private final ScheduleRule morning = onSite(MONDAY, "09:00", "12:00");

        @Test
        @DisplayName("une absence retire tous les créneaux qu'elle chevauche, même partiellement")
        void absenceRemovesOverlappingSlots() {
            var absence = new TimeRange(paris(MONDAY_NOV_2, "10:15"), paris(MONDAY_NOV_2, "11:00"));

            var slots = calculator.compute(
                    new PractitionerAgenda(List.of(morning), List.of(absence), List.of()), oneDay(MONDAY_NOV_2, 30));

            assertThat(starts(slots)).containsExactlyElementsOf(at(MONDAY_NOV_2, "09:00", "09:30", "11:00", "11:30"));
        }

        @Test
        @DisplayName("un rendez-vous retire son créneau mais pas les créneaux adjacents")
        void appointmentKeepsAdjacentSlots() {
            var appointment = new TimeRange(paris(MONDAY_NOV_2, "10:00"), paris(MONDAY_NOV_2, "10:30"));

            var slots = calculator.compute(
                    new PractitionerAgenda(List.of(morning), List.of(), List.of(appointment)), oneDay(MONDAY_NOV_2, 30));

            assertThat(starts(slots)).containsExactlyElementsOf(
                    at(MONDAY_NOV_2, "09:00", "09:30", "10:30", "11:00", "11:30"));
        }

        @Test
        @DisplayName("un rendez-vous d'un autre motif, décalé par rapport à la grille, bloque les deux créneaux touchés")
        void misalignedAppointment() {
            var appointment = new TimeRange(paris(MONDAY_NOV_2, "09:45"), paris(MONDAY_NOV_2, "10:05"));

            var slots = calculator.compute(
                    new PractitionerAgenda(List.of(morning), List.of(), List.of(appointment)), oneDay(MONDAY_NOV_2, 30));

            assertThat(starts(slots)).containsExactlyElementsOf(
                    at(MONDAY_NOV_2, "09:00", "10:30", "11:00", "11:30"));
        }
    }

    // ---------- Temps ----------

    @Nested
    @DisplayName("Gestion du temps")
    class Time {

        @Test
        @DisplayName("les créneaux déjà commencés sont retirés")
        void pastSlotsRemoved() {
            var at1010 = calculatorAt(MONDAY_NOV_2.atTime(10, 10));

            var slots = at1010.compute(agenda(List.of(onSite(MONDAY, "09:00", "12:00"))), oneDay(MONDAY_NOV_2, 30));

            assertThat(starts(slots)).containsExactlyElementsOf(at(MONDAY_NOV_2, "10:30", "11:00", "11:30"));
        }

        @Test
        @DisplayName("un créneau qui commence exactement maintenant est encore proposé")
        void slotStartingNowIsKept() {
            var at1030 = calculatorAt(MONDAY_NOV_2.atTime(10, 30));

            var slots = at1030.compute(agenda(List.of(onSite(MONDAY, "09:00", "12:00"))), oneDay(MONDAY_NOV_2, 30));

            assertThat(starts(slots).getFirst()).isEqualTo(MONDAY_NOV_2.atTime(10, 30));
        }

        @Test
        @DisplayName("9 h à Paris ne correspond pas au même instant UTC en heure d'été et en heure d'hiver")
        void daylightSavingTime() {
            // Passage à l'heure d'hiver le dimanche 25 octobre 2026
            var aroundChange = query(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26), 60, false);

            var slots = calculator.compute(agenda(List.of(onSite(MONDAY, "09:00", "10:00"))), aroundChange);

            assertThat(slots).extracting(Slot::start).containsExactly(
                    Instant.parse("2026-10-19T07:00:00Z"),   // UTC+2
                    Instant.parse("2026-10-26T08:00:00Z"));  // UTC+1
        }
    }

    // ---------- Résultat ----------

    @Nested
    @DisplayName("Forme du résultat")
    class Result {

        @Test
        @DisplayName("les créneaux de plusieurs règles sont triés chronologiquement")
        void sortedAcrossRules() {
            var rules = agenda(List.of(onSite(TUESDAY, "09:00", "10:00"), onSite(MONDAY, "14:00", "15:00")));

            var slots = calculator.compute(rules, query(MONDAY_NOV_2, MONDAY_NOV_2.plusDays(1), 60, false));

            assertThat(starts(slots)).containsExactly(
                    MONDAY_NOV_2.atTime(14, 0),
                    MONDAY_NOV_2.plusDays(1).atTime(9, 0));
        }

        @Test
        @DisplayName("deux règles identiques ne produisent pas de créneaux en double")
        void noDuplicates() {
            var rule = onSite(MONDAY, "09:00", "10:00");

            var slots = calculator.compute(agenda(List.of(rule, rule)), oneDay(MONDAY_NOV_2, 30));

            assertThat(slots).hasSize(2);
        }

        @Test
        @DisplayName("aucune règle : aucun créneau")
        void noRules() {
            assertThat(calculator.compute(agenda(List.of()), oneDay(MONDAY_NOV_2, 30))).isEmpty();
        }
    }

    // ---------- Validation des entrées ----------

    @Nested
    @DisplayName("Validation des entrées")
    class Validation {

        @Test
        @DisplayName("une période inversée est refusée")
        void invertedPeriod() {
            assertThatThrownBy(() -> query(MONDAY_NOV_2, MONDAY_NOV_2.minusDays(1), 30, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("une durée nulle est refusée (sinon la boucle de découpage ne terminerait jamais)")
        void zeroDuration() {
            assertThatThrownBy(() -> query(MONDAY_NOV_2, MONDAY_NOV_2, 0, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("une plage en présentiel sans lieu est refusée")
        void onSiteWithoutLocation() {
            assertThatThrownBy(() -> new ScheduleRule(MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                    Modality.PRESENTIEL, null, LocalDate.of(2026, 1, 1), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
