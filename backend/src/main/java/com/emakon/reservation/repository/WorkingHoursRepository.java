package com.emakon.reservation.repository;

import com.emakon.reservation.domain.Location;
import com.emakon.reservation.domain.WorkingHours;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {

    /**
     * Plages d'un praticien dont la période de validité recoupe [from, to] (bornes incluses,
     * ce sont des dates). Le filtrage fin, jour par jour, est fait par AvailabilityCalculator.
     */
    @Query("""
            select w from WorkingHours w
            where w.practitioner.id = :practitionerId
              and w.validFrom <= :to
              and (w.validUntil is null or w.validUntil >= :from)
            """)
    List<WorkingHours> findValidBetween(Long practitionerId, LocalDate from, LocalDate to);

    /**
     * Lieux où exercent les praticiens, d'après leurs plages encore valides (ou futures).
     * Une seule requête pour tous les praticiens : la liste ne fait pas une requête par ligne.
     * Les plages en distanciel n'ont pas de lieu : la jointure interne les écarte.
     */
    @Query("""
            select distinct new com.emakon.reservation.repository.PractitionerLocation(w.practitioner.id, l)
            from WorkingHours w join w.location l
            where w.validUntil is null or w.validUntil >= :today
            """)
    List<PractitionerLocation> findCurrentLocations(LocalDate today);

    @Query("""
            select distinct l
            from WorkingHours w join w.location l
            where w.practitioner.id = :practitionerId
              and (w.validUntil is null or w.validUntil >= :today)
            """)
    List<Location> findCurrentLocationsOf(Long practitionerId, LocalDate today);
}
