package com.emakon.reservation.repository;

import com.emakon.reservation.domain.Practitioner;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PractitionerRepository extends JpaRepository<Practitioner, Long> {
}
