package com.emakon.reservation.service;

import com.emakon.reservation.domain.Appointment;
import com.emakon.reservation.repository.AppointmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultation et annulation des rendez-vous, avec les règles d'autorisation :
 * un patient n'accède qu'à ses rendez-vous, un praticien qu'à ceux de son agenda.
 *
 * Ces règles sont ici et non dans Spring Security : la configuration de sécurité ne
 * connaît que l'URL et le rôle, pas le propriétaire d'un rendez-vous, qu'il faut lire
 * en base.
 */
@Service
public class AppointmentService {

    /** Une période couvre au plus 31 jours (de quoi afficher un mois d'agenda). */
    static final int MAX_PERIOD_DAYS = 31;

    private final AppointmentRepository appointments;
    private final Clock clock;
    private final ZoneId zone;

    public AppointmentService(AppointmentRepository appointments, Clock clock, ZoneId practiceZone) {
        this.appointments = appointments;
        this.clock = clock;
        this.zone = practiceZone;
    }

    @Transactional(readOnly = true)
    public AppointmentView get(Long appointmentId, AuthenticatedUser user) {
        return AppointmentView.from(loadAccessible(appointmentId, user));
    }

    /** Patient : ses rendez-vous ; praticien : son agenda. Tous statuts, par date. */
    @Transactional(readOnly = true)
    public List<AppointmentView> listFor(AuthenticatedUser user) {
        List<Appointment> result = switch (user.role()) {
            case PATIENT -> appointments.findByPatientIdOrderByStartAsc(user.id());
            case PRATICIEN -> appointments.findByPractitionerIdOrderByStartAsc(user.id());
        };
        return result.stream().map(AppointmentView::from).toList();
    }

    /**
     * Rendez-vous qui commencent entre les jours from et to inclus, jours pris dans le
     * fuseau du cabinet : [from à 00:00, lendemain de to à 00:00).
     */
    @Transactional(readOnly = true)
    public List<AppointmentView> listFor(AuthenticatedUser user, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidPeriodException("La date de fin précède la date de début");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_PERIOD_DAYS) {
            throw new InvalidPeriodException("La période ne peut pas dépasser " + MAX_PERIOD_DAYS + " jours");
        }
        // atStartOfDay(zone) et non plusHours : un jour de changement d'heure dure 23 ou 25 h.
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<Appointment> result = switch (user.role()) {
            case PATIENT -> appointments.findByPatientInPeriod(user.id(), start, end);
            case PRATICIEN -> appointments.findByPractitionerInPeriod(user.id(), start, end);
        };
        return result.stream().map(AppointmentView::from).toList();
    }

    /**
     * Pas de saveAndFlush : l'entité est gérée par la session Hibernate, la modification
     * du statut est détectée et écrite au commit (dirty checking).
     */
    @Transactional
    public AppointmentView cancel(Long appointmentId, AuthenticatedUser user) {
        Appointment appointment = loadAccessible(appointmentId, user);
        appointment.cancel(user.role(), clock.instant());
        return AppointmentView.from(appointment);
    }

    /**
     * 404 si le rendez-vous n'existe pas, 403 s'il existe mais appartient à quelqu'un
     * d'autre. Choix assumé : le 403 révèle l'existence du rendez-vous (un 404 ne
     * révélerait rien), sans en dévoiler le contenu.
     */
    private Appointment loadAccessible(Long appointmentId, AuthenticatedUser user) {
        Appointment appointment = appointments.findDetailedById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Rendez-vous introuvable : " + appointmentId));
        if (!isParticipant(appointment, user)) {
            throw new AccessForbiddenException("Ce rendez-vous ne vous concerne pas");
        }
        return appointment;
    }

    private static boolean isParticipant(Appointment appointment, AuthenticatedUser user) {
        Long participantId = switch (user.role()) {
            case PATIENT -> appointment.getPatient().getId();
            case PRATICIEN -> appointment.getPractitioner().getId();
        };
        return participantId.equals(user.id());
    }
}
