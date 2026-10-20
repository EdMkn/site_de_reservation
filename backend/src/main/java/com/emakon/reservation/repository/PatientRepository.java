package com.emakon.reservation.repository;

import com.emakon.reservation.domain.Patient;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    /** Même principe que PractitionerRepository.lockForBooking, pour l'agenda du patient. */
    @Query(value = "SELECT utilisateur_id FROM patient WHERE utilisateur_id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> lockForBooking(Long id);
}
