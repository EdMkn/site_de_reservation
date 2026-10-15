package com.emakon.reservation.repository;

import com.emakon.reservation.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {
}
