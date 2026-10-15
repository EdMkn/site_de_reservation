package com.emakon.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Le seed de démo s'applique sans erreur sur le schéma courant. Sans ce test, une
 * future migration V2 pourrait casser R__seed.sql sans que personne ne s'en aperçoive
 * avant la démo.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("demo")
@Testcontainers
class DemoSeedIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired PractitionerRepository practitioners;
    @Autowired PatientRepository patients;
    @Autowired AppointmentRepository appointments;

    @Test
    void seedIsLoaded() {
        assertThat(practitioners.count()).isEqualTo(3);
        assertThat(patients.count()).isEqualTo(2);
        assertThat(appointments.count()).isEqualTo(5);
    }

    @Test
    void cancelledAppointmentDoesNotBlockTheAgenda() {
        var martin = practitioners.findAll().stream()
                .filter(p -> p.getEmail().equals("claire.martin@example.com"))
                .findFirst().orElseThrow();
        var now = Instant.now();

        // Le seed lui donne 3 rendez-vous la semaine prochaine, dont 1 annulé
        var blocking = appointments.findConfirmedOverlapping(martin.getId(), now, now.plus(15, ChronoUnit.DAYS));

        assertThat(blocking).hasSize(2);
    }
}
