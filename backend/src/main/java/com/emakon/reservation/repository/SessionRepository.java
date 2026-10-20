package com.emakon.reservation.repository;

import com.emakon.reservation.domain.Session;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SessionRepository extends JpaRepository<Session, Long> {

    /**
     * Exécutée à chaque requête authentifiée : une seule requête SQL (index unique sur
     * token_hash), utilisateur compris grâce au join fetch.
     */
    @Query("select s from Session s join fetch s.user where s.tokenHash = :tokenHash")
    Optional<Session> findByTokenHashWithUser(String tokenHash);
}
