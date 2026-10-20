package com.emakon.reservation.repository;

import com.emakon.reservation.domain.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository sur la classe abstraite : Hibernate fait la jointure avec patient et
 * praticien, et renvoie une instance de la bonne sous-classe.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);
}
