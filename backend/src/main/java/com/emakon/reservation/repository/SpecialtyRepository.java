package com.emakon.reservation.repository;

import com.emakon.reservation.domain.Specialty;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialtyRepository extends JpaRepository<Specialty, Integer> {

    List<Specialty> findAllByOrderByLabelAsc();
}
