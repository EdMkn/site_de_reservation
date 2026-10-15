package com.emakon.reservation.repository;

import com.emakon.reservation.domain.Absence;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AbsenceRepository extends JpaRepository<Absence, Long> {

    /**
     * Absences qui chevauchent [from, to). Même condition que TimeRange.overlaps :
     * début de l'une avant la fin de l'autre, dans les deux sens.
     */
    @Query("""
            select a from Absence a
            where a.practitioner.id = :practitionerId
              and a.start < :to
              and a.end > :from
            """)
    List<Absence> findOverlapping(Long practitionerId, Instant from, Instant to);
}
