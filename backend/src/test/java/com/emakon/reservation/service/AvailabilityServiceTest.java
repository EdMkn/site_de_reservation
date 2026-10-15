package com.emakon.reservation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.emakon.reservation.availability.AvailabilityCalculator;
import com.emakon.reservation.domain.ConsultationType;
import com.emakon.reservation.domain.Practitioner;
import com.emakon.reservation.repository.AbsenceRepository;
import com.emakon.reservation.repository.AppointmentRepository;
import com.emakon.reservation.repository.ConsultationTypeRepository;
import com.emakon.reservation.repository.PractitionerRepository;
import com.emakon.reservation.repository.WorkingHoursRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test unitaire du service : les repositories sont remplacés par des mocks Mockito,
 * donc ni base ni Spring. On vérifie la logique propre au service (validations, 404,
 * conversion de la période en instants), pas le calcul lui-même (déjà testé à part).
 */
class AvailabilityServiceTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Long DOCTOR_ID = 1L;
    private static final Long TYPE_ID = 10L;
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    private final PractitionerRepository practitioners = mock(PractitionerRepository.class);
    private final ConsultationTypeRepository consultationTypes = mock(ConsultationTypeRepository.class);
    private final WorkingHoursRepository workingHours = mock(WorkingHoursRepository.class);
    private final AbsenceRepository absences = mock(AbsenceRepository.class);
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);

    private AvailabilityService service;

    @BeforeEach
    void setUp() {
        // Le calculateur est du Java pur : on utilise le vrai, avec une horloge figée.
        var calculator = new AvailabilityCalculator(Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), PARIS), PARIS);
        service = new AvailabilityService(practitioners, consultationTypes, workingHours, absences, appointments,
                calculator, PARIS);
    }

    private void givenExistingDoctorAndType() {
        var doctor = new Practitioner("dr@example.com", "hash", "A", "A", "11111111111");
        when(practitioners.existsById(DOCTOR_ID)).thenReturn(true);
        when(consultationTypes.findByIdAndPractitionerIdAndActiveTrue(TYPE_ID, DOCTOR_ID))
                .thenReturn(Optional.of(new ConsultationType(doctor, "Consultation", Duration.ofMinutes(30), false)));
        when(workingHours.findValidBetween(anyLong(), any(), any())).thenReturn(List.of());
        when(absences.findOverlapping(anyLong(), any(), any())).thenReturn(List.of());
        when(appointments.findConfirmedOverlapping(anyLong(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("la période est convertie en instants à minuit, heure de Paris, fin exclue")
    void periodBoundsUsePracticeTimezone() {
        givenExistingDoctorAndType();

        service.findAvailableSlots(DOCTOR_ID, TYPE_ID, MONDAY, MONDAY.plusDays(6));

        // Lundi 2 novembre 00:00 à Paris (UTC+1) = 1er novembre 23:00 UTC ;
        // fin = lundi suivant 00:00 à Paris, pour inclure tout le dimanche.
        var start = Instant.parse("2026-11-01T23:00:00Z");
        var end = Instant.parse("2026-11-08T23:00:00Z");
        verify(absences).findOverlapping(DOCTOR_ID, start, end);
        verify(appointments).findConfirmedOverlapping(DOCTOR_ID, start, end);
        verify(workingHours).findValidBetween(DOCTOR_ID, MONDAY, MONDAY.plusDays(6));
    }

    @Test
    @DisplayName("praticien inconnu : NotFoundException")
    void unknownPractitioner() {
        when(practitioners.existsById(DOCTOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.findAvailableSlots(DOCTOR_ID, TYPE_ID, MONDAY, MONDAY))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("motif inconnu, inactif ou d'un autre praticien : NotFoundException")
    void unknownConsultationType() {
        when(practitioners.existsById(DOCTOR_ID)).thenReturn(true);
        when(consultationTypes.findByIdAndPractitionerIdAndActiveTrue(TYPE_ID, DOCTOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findAvailableSlots(DOCTOR_ID, TYPE_ID, MONDAY, MONDAY))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("période inversée : refusée avant toute requête en base")
    void invertedPeriod() {
        assertThatThrownBy(() -> service.findAvailableSlots(DOCTOR_ID, TYPE_ID, MONDAY, MONDAY.minusDays(1)))
                .isInstanceOf(InvalidPeriodException.class);
        verifyNoInteractions(practitioners, consultationTypes, workingHours, absences, appointments);
    }

    @Test
    @DisplayName("31 jours sont acceptés, 32 refusés")
    void periodLengthLimit() {
        givenExistingDoctorAndType();

        service.findAvailableSlots(DOCTOR_ID, TYPE_ID, MONDAY, MONDAY.plusDays(30));   // 31 jours, bornes incluses
        assertThatThrownBy(() -> service.findAvailableSlots(DOCTOR_ID, TYPE_ID, MONDAY, MONDAY.plusDays(31)))
                .isInstanceOf(InvalidPeriodException.class);
    }
}
