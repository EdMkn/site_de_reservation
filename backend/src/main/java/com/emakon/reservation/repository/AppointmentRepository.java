package com.emakon.reservation.repository;

import com.emakon.reservation.domain.Appointment;
import com.emakon.reservation.domain.AppointmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * Rendez-vous CONFIRME d'un praticien qui chevauchent [from, to) : ceux qui bloquent
     * l'agenda. Les annulés, honorés ou absents ne comptent pas, comme dans la contrainte
     * d'exclusion SQL (WHERE statut = 'CONFIRME').
     */
    default List<Appointment> findConfirmedOverlapping(Long practitionerId, Instant from, Instant to) {
        return findOverlappingWithStatus(practitionerId, AppointmentStatus.CONFIRME, from, to);
    }

    /**
     * Le statut est passé en paramètre, et non écrit en littéral dans la requête : pour un
     * littéral, Hibernate génère 'CONFIRME'::AppointmentStatus (nom de la classe Java, pas
     * du type SQL statut_rdv) et PostgreSQL échoue. Un paramètre est envoyé sans cast.
     */
    @Query("""
            select a from Appointment a
            where a.practitioner.id = :practitionerId
              and a.status = :status
              and a.start < :to
              and a.end > :from
            """)
    List<Appointment> findOverlappingWithStatus(Long practitionerId, AppointmentStatus status, Instant from, Instant to);

    // @EntityGraph : patient, praticien, motif et lieu chargés dans la même requête
    // (jointures). Sans lui, afficher une liste de N rendez-vous déclencherait jusqu'à
    // 4 requêtes de plus par ligne pour initialiser les relations LAZY (problème N+1).

    @EntityGraph(attributePaths = {"patient", "practitioner", "consultationType", "location"})
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"patient", "practitioner", "consultationType", "location"})
    List<Appointment> findByPatientIdOrderByStartAsc(Long patientId);

    @EntityGraph(attributePaths = {"patient", "practitioner", "consultationType", "location"})
    List<Appointment> findByPractitionerIdOrderByStartAsc(Long practitionerId);

    // Variantes bornées : rendez-vous qui COMMENCENT dans [from, to). Servies par les index
    // (patient_id, debut) et (praticien_id, debut) de V1__init.sql : PostgreSQL ne lit que
    // les lignes de la période, quel que soit le nombre d'années d'historique.

    @EntityGraph(attributePaths = {"patient", "practitioner", "consultationType", "location"})
    @Query("""
            select a from Appointment a
            where a.patient.id = :patientId and a.start >= :from and a.start < :to
            order by a.start
            """)
    List<Appointment> findByPatientInPeriod(Long patientId, Instant from, Instant to);

    @EntityGraph(attributePaths = {"patient", "practitioner", "consultationType", "location"})
    @Query("""
            select a from Appointment a
            where a.practitioner.id = :practitionerId and a.start >= :from and a.start < :to
            order by a.start
            """)
    List<Appointment> findByPractitionerInPeriod(Long practitionerId, Instant from, Instant to);
}
