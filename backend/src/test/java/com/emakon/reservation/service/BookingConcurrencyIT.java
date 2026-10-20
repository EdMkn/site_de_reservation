package com.emakon.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.emakon.reservation.availability.Modality;
import com.emakon.reservation.domain.ConsultationType;
import com.emakon.reservation.domain.Location;
import com.emakon.reservation.domain.Patient;
import com.emakon.reservation.domain.Practitioner;
import com.emakon.reservation.domain.WorkingHours;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Réservations simultanées : la garantie vient de PostgreSQL, pas du code Java.
 *
 * Volontairement SANS @Transactional sur la classe : chaque thread doit avoir sa propre
 * transaction, et voir des données de départ réellement commitées. En contrepartie,
 * rien n'est annulé automatiquement : @AfterEach vide les tables.
 */
@SpringBootTest
@Testcontainers
class BookingConcurrencyIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    /** Lundi 2 novembre 2026, 10h à Paris (UTC+1). */
    private static final Instant SLOT = Instant.parse("2026-11-02T09:00:00Z");

    // Inférieur à la taille du pool de connexions (10 par défaut) : chaque thread obtient
    // la sienne, et aucun ne bloque en attendant une connexion libre.
    private static final int THREADS = 8;

    @Autowired BookingService booking;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private Location cabinet;

    @AfterEach
    void cleanUp() {
        // CASCADE : vide aussi patient, praticien, motif, plage_horaire, rendez_vous...
        jdbc.execute("TRUNCATE utilisateur, lieu, specialite RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("N patients, même praticien, même créneau, au même instant : un seul rendez-vous")
    void onlyOneBookingWinsTheSlot() throws Exception {
        ConsultationType type = tx.execute(s -> doctorWorkingMondayMorning("dr@example.com", "11111111111"));
        List<Long> patientIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            patientIds.add(tx.execute(s -> newPatient()));
        }

        List<Callable<Long>> attempts = patientIds.stream()
                .<Callable<Long>>map(pid -> () -> booking.book(pid, type.getPractitioner().getId(), type.getId(),
                        Modality.PRESENTIEL, cabinet.getId(), SLOT, null))
                .toList();
        Outcome outcome = runSimultaneously(attempts);

        assertThat(outcome.successes()).isEqualTo(1);
        // Les perdants sont refusés soit par la vérification applicative (le gagnant a déjà
        // commité), soit par la contrainte d'exclusion (course) : même exception dans les deux cas.
        assertThat(outcome.failures()).hasSize(THREADS - 1)
                .allMatch(e -> e instanceof SlotUnavailableException);
        assertThat(confirmedAppointments()).isEqualTo(1);
    }

    @Test
    @DisplayName("même patient, deux praticiens, même heure, au même instant : un seul rendez-vous")
    void patientCannotBeBookedTwiceAtTheSameTime() throws Exception {
        ConsultationType typeA = tx.execute(s -> doctorWorkingMondayMorning("a@example.com", "11111111111"));
        ConsultationType typeB = tx.execute(s -> doctorWorkingMondayMorning("b@example.com", "22222222222"));
        Long patientId = tx.execute(s -> newPatient());

        // Les deux agendas sont libres : la vérification applicative laisse passer les deux,
        // seule la contrainte côté patient peut départager.
        Outcome outcome = runSimultaneously(List.of(
                () -> booking.book(patientId, typeA.getPractitioner().getId(), typeA.getId(),
                        Modality.PRESENTIEL, cabinet.getId(), SLOT, null),
                () -> booking.book(patientId, typeB.getPractitioner().getId(), typeB.getId(),
                        Modality.PRESENTIEL, cabinet.getId(), SLOT, null)));

        assertThat(outcome.successes()).isEqualTo(1);
        assertThat(outcome.failures()).singleElement().isInstanceOf(PatientUnavailableException.class);
        assertThat(confirmedAppointments()).isEqualTo(1);
    }

    // --- Outils ---

    private record Outcome(int successes, List<Throwable> failures) {
    }

    /**
     * Lance toutes les tentatives au même instant. Chaque thread signale qu'il est prêt,
     * puis attend le départ : sans ce latch, le premier thread aurait souvent fini avant
     * que le dernier ne démarre, et il n'y aurait aucune concurrence réelle.
     */
    private static Outcome runSimultaneously(List<Callable<Long>> attempts) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(attempts.size());
        CountDownLatch ready = new CountDownLatch(attempts.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (Callable<Long> attempt : attempts) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return attempt.call();
                }));
            }
            ready.await();
            start.countDown();

            int successes = 0;
            List<Throwable> failures = new ArrayList<>();
            for (Future<Long> f : futures) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                    successes++;
                } catch (java.util.concurrent.ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return new Outcome(successes, failures);
        } finally {
            pool.shutdownNow();
        }
    }

    private ConsultationType doctorWorkingMondayMorning(String email, String rpps) {
        if (cabinet == null) {
            cabinet = new Location("Cabinet", "1 rue A", "75001", "Paris");
            em.persist(cabinet);
        }
        var doctor = new Practitioner(email, "hash", "Martin", "Claire", rpps);
        em.persist(doctor);
        em.persist(new WorkingHours(doctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0),
                Modality.PRESENTIEL, em.merge(cabinet), LocalDate.of(2026, 1, 1), null));
        var type = new ConsultationType(doctor, "Consultation", Duration.ofMinutes(30), false);
        em.persist(type);
        return type;
    }

    private Long newPatient() {
        var patient = new Patient("p" + System.nanoTime() + "@example.com", "hash", "Dupont", "Jean",
                LocalDate.of(1990, 1, 1));
        em.persist(patient);
        return patient.getId();
    }

    private long confirmedAppointments() {
        return jdbc.queryForObject("SELECT count(*) FROM rendez_vous WHERE statut = 'CONFIRME'", Long.class);
    }
}
