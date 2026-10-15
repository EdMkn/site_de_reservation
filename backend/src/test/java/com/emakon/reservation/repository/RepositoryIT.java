package com.emakon.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.emakon.reservation.availability.Modality;
import com.emakon.reservation.domain.Absence;
import com.emakon.reservation.domain.Appointment;
import com.emakon.reservation.domain.ConsultationType;
import com.emakon.reservation.domain.Patient;
import com.emakon.reservation.domain.Practitioner;
import com.emakon.reservation.domain.WorkingHours;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Requêtes "sur une période" des repositories. L'essentiel se joue aux bornes :
 * ce qui touche la période sans la chevaucher ne doit pas être renvoyé.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class RepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestEntityManager em;
    @Autowired WorkingHoursRepository workingHours;
    @Autowired AbsenceRepository absences;
    @Autowired AppointmentRepository appointments;
    @Autowired ConsultationTypeRepository consultationTypes;

    private Practitioner doctor;
    private Practitioner otherDoctor;

    // Période interrogée : la journée du lundi 2 novembre 2026, 08:00Z -> 18:00Z
    private static final Instant FROM = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-02T18:00:00Z");

    @BeforeEach
    void setUp() {
        doctor = em.persist(new Practitioner("dr.a@example.com", "hash", "A", "A", "11111111111"));
        otherDoctor = em.persist(new Practitioner("dr.b@example.com", "hash", "B", "B", "22222222222"));
    }

    private static Instant at(String time) {
        return Instant.parse("2026-11-02T" + time + ":00Z");
    }

    @Nested
    class WorkingHoursValidity {

        private WorkingHours persistHours(Practitioner p, LocalDate validFrom, LocalDate validUntil) {
            return em.persist(new WorkingHours(p, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0),
                    Modality.DISTANCIEL, null, validFrom, validUntil));
        }

        @Test
        void returnsOnlyRulesValidDuringThePeriod() {
            var openEnded = persistHours(doctor, LocalDate.of(2026, 1, 1), null);
            var endsOnFirstDay = persistHours(doctor, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 2)); // borne incluse
            var startsOnLastDay = persistHours(doctor, LocalDate.of(2026, 11, 8), null);                     // borne incluse
            persistHours(doctor, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 1));   // finie la veille
            persistHours(doctor, LocalDate.of(2026, 11, 9), null);                       // commence après
            persistHours(otherDoctor, LocalDate.of(2026, 1, 1), null);                   // autre praticien

            var result = workingHours.findValidBetween(doctor.getId(), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8));

            assertThat(result).containsExactlyInAnyOrder(openEnded, endsOnFirstDay, startsOnLastDay);
        }
    }

    @Nested
    class AbsenceOverlap {

        @Test
        void returnsOnlyAbsencesOverlappingThePeriod() {
            var inside = em.persist(new Absence(doctor, at("10:00"), at("11:00"), null));
            var straddlingStart = em.persist(new Absence(doctor, at("06:00"), at("09:00"), null));
            var covering = em.persist(new Absence(doctor, at("00:00"), Instant.parse("2026-11-03T00:00:00Z"), null));
            em.persist(new Absence(doctor, at("06:00"), at("08:00"), null));   // se termine pile au début
            em.persist(new Absence(doctor, at("18:00"), at("20:00"), null));   // commence pile à la fin
            em.persist(new Absence(otherDoctor, at("10:00"), at("11:00"), null));

            assertThat(absences.findOverlapping(doctor.getId(), FROM, TO))
                    .containsExactlyInAnyOrder(inside, straddlingStart, covering);
        }
    }

    @Nested
    class ConfirmedAppointments {

        private Patient patient;
        private ConsultationType type;

        @BeforeEach
        void setUp() {
            patient = em.persist(new Patient("p@example.com", "hash", "P", "P", LocalDate.of(1990, 1, 1)));
            type = em.persist(new ConsultationType(doctor, "Consultation", Duration.ofMinutes(30), true));
        }

        private Appointment book(String time) {
            return em.persist(new Appointment(patient, type, Modality.DISTANCIEL, null, at(time), null));
        }

        @Test
        void ignoresCancelledAndAdjacentAppointments() {
            var confirmed = book("10:00");
            var cancelled = book("11:00");
            book("07:30");   // 07:30–08:00 : se termine pile au début de la période
            book("18:00");   // commence pile à la fin
            em.flush();

            // Pas encore de méthode d'annulation (étape 5) : on passe par SQL.
            em.getEntityManager()
                    .createNativeQuery("update rendez_vous set statut = 'ANNULE_PATIENT', annule_le = now() where id = :id")
                    .setParameter("id", cancelled.getId())
                    .executeUpdate();
            em.clear();

            assertThat(appointments.findConfirmedOverlapping(doctor.getId(), FROM, TO))
                    .extracting(Appointment::getId)
                    .containsExactly(confirmed.getId());
        }
    }

    @Nested
    class ConsultationTypeOwnership {

        @Test
        void findsOnlyActiveTypesOfThatPractitioner() {
            var own = em.persist(new ConsultationType(doctor, "Consultation", Duration.ofMinutes(20), false));
            var inactive = new ConsultationType(doctor, "Ancien motif", Duration.ofMinutes(20), false);
            inactive.deactivate();
            em.persist(inactive);
            var foreign = em.persist(new ConsultationType(otherDoctor, "Autre", Duration.ofMinutes(20), false));

            assertThat(consultationTypes.findByIdAndPractitionerIdAndActiveTrue(own.getId(), doctor.getId())).contains(own);
            assertThat(consultationTypes.findByIdAndPractitionerIdAndActiveTrue(inactive.getId(), doctor.getId())).isEmpty();
            assertThat(consultationTypes.findByIdAndPractitionerIdAndActiveTrue(foreign.getId(), doctor.getId())).isEmpty();
        }
    }
}
