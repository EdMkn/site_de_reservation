package com.emakon.reservation.repository;

import com.emakon.reservation.domain.Appointment;
import com.emakon.reservation.domain.AppointmentStatus;
import java.time.Instant;
import java.util.List;
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
}
