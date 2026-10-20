package com.emakon.reservation.repository;

import com.emakon.reservation.domain.Practitioner;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PractitionerRepository extends JpaRepository<Practitioner, Long> {

    /**
     * Verrou sur la ligne du praticien jusqu'à la fin de la transaction : deux réservations
     * sur son agenda s'exécutent l'une après l'autre (voir BookingService).
     *
     * Requête native, sur la seule table praticien : un verrou JPA sur l'entité (héritage
     * JOINED) verrouillerait aussi la ligne utilisateur, et bloquerait sans raison la
     * création de sessions, dont la clé étrangère vise utilisateur.
     *
     * @return vide si le praticien n'existe pas
     */
    @Query(value = "SELECT utilisateur_id FROM praticien WHERE utilisateur_id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> lockForBooking(Long id);

    /** Praticiens actifs, spécialités chargées dans la même requête (pas de N+1). */
    @EntityGraph(attributePaths = "specialties")
    List<Practitioner> findByActiveTrueOrderByLastNameAscFirstNameAsc();
}
