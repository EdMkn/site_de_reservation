package com.emakon.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emakon.reservation.availability.Modality;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Règles d'annulation, testées sans base : l'entité est un simple objet Java. */
class AppointmentCancellationTest {

    private static final Instant START = Instant.parse("2026-11-02T09:00:00Z");

    private static Appointment confirmed() {
        var doctor = new Practitioner("dr@example.com", "hash", "Martin", "Claire", "11111111111");
        var type = new ConsultationType(doctor, "Consultation", Duration.ofMinutes(30), true);
        var patient = new Patient("p@example.com", "hash", "Dupont", "Jean", LocalDate.of(1990, 1, 1));
        return new Appointment(patient, type, Modality.DISTANCIEL, null, START, null);
    }

    @Test
    @DisplayName("patient, plus de 24 h avant : annulé par le patient, date d'annulation renseignée")
    void patientCancelsInTime() {
        var appointment = confirmed();
        Instant now = START.minus(Duration.ofDays(2));

        appointment.cancel(Role.PATIENT, now);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.ANNULE_PATIENT);
        assertThat(appointment.getCancelledAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("patient, exactement 24 h avant : accepté ; une seconde plus tard : refusé")
    void patientNoticeBoundary() {
        var inTime = confirmed();
        inTime.cancel(Role.PATIENT, START.minus(Duration.ofHours(24)));
        assertThat(inTime.getStatus()).isEqualTo(AppointmentStatus.ANNULE_PATIENT);

        var tooLate = confirmed();
        assertThatThrownBy(() -> tooLate.cancel(Role.PATIENT, START.minus(Duration.ofHours(24)).plusSeconds(1)))
                .isInstanceOf(CancellationNotAllowedException.class);
        assertThat(tooLate.getStatus()).isEqualTo(AppointmentStatus.CONFIRME);
    }

    @Test
    @DisplayName("praticien, une heure avant : accepté, statut ANNULE_PRATICIEN")
    void practitionerCancelsLate() {
        var appointment = confirmed();

        appointment.cancel(Role.PRATICIEN, START.minus(Duration.ofHours(1)));

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.ANNULE_PRATICIEN);
    }

    @Test
    @DisplayName("praticien, après le début : refusé")
    void practitionerCannotCancelStartedAppointment() {
        var appointment = confirmed();

        assertThatThrownBy(() -> appointment.cancel(Role.PRATICIEN, START))
                .isInstanceOf(CancellationNotAllowedException.class);
    }

    @Test
    @DisplayName("déjà annulé : refusé")
    void cannotCancelTwice() {
        var appointment = confirmed();
        Instant now = START.minus(Duration.ofDays(2));
        appointment.cancel(Role.PATIENT, now);

        assertThatThrownBy(() -> appointment.cancel(Role.PRATICIEN, now))
                .isInstanceOf(CancellationNotAllowedException.class);
    }
}
