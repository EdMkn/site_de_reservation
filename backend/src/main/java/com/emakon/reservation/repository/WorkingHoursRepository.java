package com.emakon.reservation.repository;

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
}
