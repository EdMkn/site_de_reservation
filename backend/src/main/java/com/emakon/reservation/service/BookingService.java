package com.emakon.reservation.service;

import com.emakon.reservation.availability.Modality;
import com.emakon.reservation.availability.Slot;
import com.emakon.reservation.domain.Appointment;
import com.emakon.reservation.domain.ConsultationType;
import com.emakon.reservation.domain.Location;
import com.emakon.reservation.domain.Patient;
import com.emakon.reservation.repository.AppointmentRepository;
import com.emakon.reservation.repository.ConsultationTypeRepository;
import com.emakon.reservation.repository.LocationRepository;
import com.emakon.reservation.repository.PatientRepository;
import com.emakon.reservation.repository.PractitionerRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cas d'usage "réserver un créneau".
 *
 * Protection contre la double réservation, en trois couches :
 * 1. verrous sur les lignes du praticien et du patient : les réservations concurrentes
 *    sur un même agenda passent l'une après l'autre ;
 * 2. vérification applicative (le créneau figure dans les disponibilités calculées) :
 *    donne un message clair ;
 * 3. contraintes d'exclusion PostgreSQL : la garantie finale, même si un code futur
 *    oublie les deux premières (et seule à voir les rendez-vous du patient chez un
 *    autre praticien).
 */
@Service
public class BookingService {

    // Noms déclarés dans V1__init.sql : les renommer là-bas impose de les renommer ici.
    static final String PRACTITIONER_OVERLAP = "excl_rendez_vous_praticien_chevauchement";
    static final String PATIENT_OVERLAP = "excl_rendez_vous_patient_chevauchement";

    private final PractitionerRepository practitioners;
    private final PatientRepository patients;
    private final ConsultationTypeRepository consultationTypes;
    private final LocationRepository locations;
    private final AppointmentRepository appointments;
    private final AvailabilityService availability;
    private final ZoneId zone;

    public BookingService(PractitionerRepository practitioners,
                          PatientRepository patients,
                          ConsultationTypeRepository consultationTypes,
                          LocationRepository locations,
                          AppointmentRepository appointments,
                          AvailabilityService availability,
                          ZoneId practiceZone) {
        this.practitioners = practitioners;
        this.patients = patients;
        this.consultationTypes = consultationTypes;
        this.locations = locations;
        this.appointments = appointments;
        this.availability = availability;
        this.zone = practiceZone;
    }

    /**
     * Une seule transaction pour tout le cas d'usage. L'appel à AvailabilityService passe
     * par son proxy Spring : avec la propagation par défaut (REQUIRED), il rejoint cette
     * transaction au lieu d'en ouvrir une autre.
     *
     * @param locationId null en distanciel
     * @return identifiant du rendez-vous créé
     */
    @Transactional
    public Long book(Long patientId, Long practitionerId, Long consultationTypeId,
                     Modality modality, Long locationId, Instant start, String patientNote) {
        // 0. Verrous, AVANT la vérification du point 3.
        //
        //    Sans eux, deux réservations simultanées du même créneau passent toutes deux le
        //    point 3, puis s'interbloquent à l'INSERT : chacune voit dans l'index de la
        //    contrainte d'exclusion la ligne non commitée de l'autre, et attend sa fin.
        //    PostgreSQL annule alors l'une des deux (deadlock, SQLSTATE 40P01) : pas de
        //    double réservation, mais une erreur 500 au lieu d'un 409.
        //
        //    Avec eux, la seconde attend ici que la première se termine. En READ COMMITTED
        //    (niveau par défaut de PostgreSQL), chaque requête voit les données commitées à
        //    son début : le point 3 de la seconde voit donc le rendez-vous de la première,
        //    et répond proprement "créneau indisponible".
        //
        //    Toujours dans le même ordre, praticien puis patient : deux transactions qui
        //    verrouillent dans le même ordre ne peuvent pas s'attendre mutuellement.
        practitioners.lockForBooking(practitionerId);
        patients.lockForBooking(patientId);

        // 1. Patient : l'identifiant vient du jeton (utilisateur connecté), pas du client.
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Patient introuvable : " + patientId));

        // 2. Motif actif du praticien. Couvre aussi le praticien inexistant : aucun motif
        //    ne peut lui appartenir.
        ConsultationType type = consultationTypes
                .findByIdAndPractitionerIdAndActiveTrue(consultationTypeId, practitionerId)
                .orElseThrow(() -> new NotFoundException(
                        "Motif " + consultationTypeId + " introuvable pour le praticien " + practitionerId));

        // 3. Le créneau demandé doit être l'un de ceux que propose le calcul : une seule
        //    source de vérité pour "réservable" (futur, dans le planning, hors absences et
        //    rendez-vous, modalité permise par le motif, bon lieu). Slot est un record :
        //    contains() compare les quatre champs.
        LocalDate day = LocalDate.ofInstant(start, zone);
        var requested = new Slot(start, start.plus(type.getDuration()), modality, locationId);
        if (!availability.findAvailableSlots(practitionerId, consultationTypeId, day, day).contains(requested)) {
            throw new SlotUnavailableException("Ce créneau n'est pas disponible");
        }

        // 4. getReferenceById : pas de requête, juste un proxy portant l'id. Sans risque ici,
        //    le point 3 a prouvé que ce lieu est celui d'une plage du praticien.
        Location location = locationId == null ? null : locations.getReferenceById(locationId);
        var appointment = new Appointment(patient, type, modality, location, start, patientNote);

        // 5. saveAndFlush force l'INSERT maintenant : une violation de contrainte est levée
        //    ici, où on peut la traduire, et non au commit, après la sortie de la méthode.
        try {
            return appointments.saveAndFlush(appointment).getId();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    /**
     * Le chevauchement côté praticien est le cas typique de la course entre deux
     * réservations ; côté patient, la vérification du point 3 ne le voit pas du tout (le
     * calcul ne regarde que l'agenda du praticien), seule la contrainte l'attrape.
     * Toute autre violation est un bug : on la laisse remonter (HTTP 500).
     */
    private static RuntimeException translate(DataIntegrityViolationException e) {
        String constraint = constraintName(e);
        if (PRACTITIONER_OVERLAP.equals(constraint)) {
            return new SlotUnavailableException("Ce créneau vient d'être réservé");
        }
        if (PATIENT_OVERLAP.equals(constraint)) {
            return new PatientUnavailableException("Vous avez déjà un rendez-vous sur ce créneau");
        }
        return e;
    }

    /**
     * Chaîne des causes : DataIntegrityViolationException (Spring) -> ConstraintViolationException
     * (Hibernate) -> PSQLException (driver). Hibernate 6 n'extrait pas le nom pour une
     * contrainte d'exclusion (getConstraintName() renvoie null) : on le lit dans l'erreur
     * renvoyée par le serveur PostgreSQL.
     */
    private static String constraintName(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
                return psql.getServerErrorMessage().getConstraint();
            }
        }
        return null;
    }
}
