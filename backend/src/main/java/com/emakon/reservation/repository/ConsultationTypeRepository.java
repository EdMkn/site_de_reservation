package com.emakon.reservation.repository;

import com.emakon.reservation.domain.ConsultationType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationTypeRepository extends JpaRepository<ConsultationType, Long> {

    /**
     * Le motif demandé, à condition qu'il appartienne à ce praticien et soit encore proposé.
     * Requête dérivée du nom de la méthode : Spring Data traduit "PractitionerId" en
     * practitioner.id et "ActiveTrue" en active = true.
     */
    Optional<ConsultationType> findByIdAndPractitionerIdAndActiveTrue(Long id, Long practitionerId);
}
