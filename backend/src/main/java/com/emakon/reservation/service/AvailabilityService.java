package com.emakon.reservation.service;

import com.emakon.reservation.availability.AvailabilityCalculator;
import com.emakon.reservation.availability.AvailabilityQuery;
import com.emakon.reservation.availability.PractitionerAgenda;
import com.emakon.reservation.availability.ScheduleRule;
import com.emakon.reservation.availability.Slot;
import com.emakon.reservation.availability.TimeRange;
import com.emakon.reservation.domain.ConsultationType;
import com.emakon.reservation.domain.WorkingHours;
import com.emakon.reservation.repository.AbsenceRepository;
import com.emakon.reservation.repository.AppointmentRepository;
import com.emakon.reservation.repository.ConsultationTypeRepository;
import com.emakon.reservation.repository.PractitionerRepository;
import com.emakon.reservation.repository.WorkingHoursRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cas d'usage "voir les créneaux libres d'un praticien pour un motif".
 *
 * Fait le lien entre la base et le calcul : charge les données (repositories), les
 * convertit en types simples du package availability, puis délègue le calcul.
 */
@Service
public class AvailabilityService {

    /** Au-delà, le calcul et la réponse deviennent lourds ; le frontend pagine par semaine. */
    static final int MAX_PERIOD_DAYS = 31;

    private final PractitionerRepository practitioners;
    private final ConsultationTypeRepository consultationTypes;
    private final WorkingHoursRepository workingHours;
    private final AbsenceRepository absences;
    private final AppointmentRepository appointments;
    private final AvailabilityCalculator calculator;
    private final ZoneId zone;

    // Injection par constructeur : dépendances explicites, champs final, et la classe
    // reste instanciable sans Spring (test unitaire avec des mocks).
    public AvailabilityService(PractitionerRepository practitioners,
                               ConsultationTypeRepository consultationTypes,
                               WorkingHoursRepository workingHours,
                               AbsenceRepository absences,
                               AppointmentRepository appointments,
                               AvailabilityCalculator calculator,
                               ZoneId practiceZone) {
        this.practitioners = practitioners;
        this.consultationTypes = consultationTypes;
        this.workingHours = workingHours;
        this.absences = absences;
        this.appointments = appointments;
        this.calculator = calculator;
        this.zone = practiceZone;
    }

    /**
     * readOnly : aucune écriture attendue ; Hibernate se passe alors de la détection des
     * modifications (dirty checking) en fin de transaction. Une seule transaction pour
     * toutes les lectures : elles voient un état cohérent de la base.
     *
     * @param from premier jour, inclus
     * @param to   dernier jour, inclus
     */
    @Transactional(readOnly = true)
    public List<Slot> findAvailableSlots(Long practitionerId, Long consultationTypeId, LocalDate from, LocalDate to) {
        checkPeriod(from, to);
        if (!practitioners.existsById(practitionerId)) {
            throw new NotFoundException("Praticien introuvable : " + practitionerId);
        }
        ConsultationType type = consultationTypes
                .findByIdAndPractitionerIdAndActiveTrue(consultationTypeId, practitionerId)
                .orElseThrow(() -> new NotFoundException(
                        "Motif " + consultationTypeId + " introuvable pour le praticien " + practitionerId));

        // Bornes de la période en instants : de minuit (heure du cabinet) du premier jour
        // à minuit du lendemain du dernier jour.
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

        var agenda = new PractitionerAgenda(
                workingHours.findValidBetween(practitionerId, from, to).stream()
                        .map(AvailabilityService::toRule)
                        .toList(),
                absences.findOverlapping(practitionerId, start, end).stream()
                        .map(a -> new TimeRange(a.getStart(), a.getEnd()))
                        .toList(),
                appointments.findConfirmedOverlapping(practitionerId, start, end).stream()
                        .map(a -> new TimeRange(a.getStart(), a.getEnd()))
                        .toList());

        return calculator.compute(agenda, new AvailabilityQuery(from, to, type.getDuration(), type.isRemoteAllowed()));
    }

    private static void checkPeriod(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidPeriodException("La date de fin précède la date de début");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_PERIOD_DAYS) {
            throw new InvalidPeriodException("La période ne peut pas dépasser " + MAX_PERIOD_DAYS + " jours");
        }
    }

    private static ScheduleRule toRule(WorkingHours w) {
        // getId() sur un proxy LAZY ne déclenche pas de requête : Hibernate connaît déjà la
        // clé étrangère. Le lieu n'est donc pas chargé, seul son identifiant est lu.
        Long locationId = w.getLocation() == null ? null : w.getLocation().getId();
        return new ScheduleRule(w.getDay(), w.getStartTime(), w.getEndTime(), w.getModality(),
                locationId, w.getValidFrom(), w.getValidUntil());
    }
}
