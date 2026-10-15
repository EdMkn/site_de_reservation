package com.emakon.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.emakon.reservation.availability.Modality;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Aller-retour Java -> PostgreSQL -> Java pour chaque entité.
 *
 * ddl-auto=validate vérifie les noms et types de colonnes, mais pas qu'une valeur
 * s'écrit et se relit correctement (enums PostgreSQL, CHAR, jour de la semaine, valeurs
 * générées par la base). C'est ce que vérifie ce test.
 *
 * @DataJpaTest ne démarre que la couche JPA (pas le web ni la sécurité) et annule la
 * transaction à la fin de chaque test : la base reste propre.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // garder le vrai PostgreSQL
@Testcontainers
class PersistenceMappingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TestEntityManager em;

    @Test
    void fullAgendaRoundTrip() {
        // given : un graphe complet écrit en base
        var cabinet = em.persist(new Location("Cabinet des Ternes", "12 rue Bayen", "75017", "Paris"));
        var dermato = em.persist(new Specialty("Dermatologie"));

        var doctor = new Practitioner("dr.martin@example.com", "hash", "Martin", "Claire", "10101010101");
        doctor.addSpecialty(dermato);
        em.persist(doctor);

        var patient = em.persist(new Patient("jean@example.com", "hash", "Dupont", "Jean", LocalDate.of(1990, 5, 17)));
        var firstVisit = em.persist(new ConsultationType(doctor, "Première consultation", Duration.ofMinutes(30), true));
        em.persist(new WorkingHours(doctor, DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0),
                Modality.PRESENTIEL, cabinet, LocalDate.of(2026, 9, 1), null));
        em.persist(new Absence(doctor, Instant.parse("2026-12-24T00:00:00Z"), Instant.parse("2026-12-26T00:00:00Z"), "Congés"));
        var appointment = em.persist(new Appointment(patient, firstVisit, Modality.PRESENTIEL, cabinet,
                Instant.parse("2026-11-03T08:00:00Z"), null));

        // flush : envoie les INSERT maintenant ; clear : vide le cache de la session,
        // pour que les lectures suivantes viennent vraiment de la base.
        em.flush();
        em.clear();

        // then : tout se relit à l'identique
        var reloadedDoctor = em.find(Practitioner.class, doctor.getId());
        assertThat(reloadedDoctor.getRole()).isEqualTo(Role.PRATICIEN);
        assertThat(reloadedDoctor.getRppsNumber()).isEqualTo("10101010101");
        assertThat(reloadedDoctor.getCreatedAt()).isNotNull();   // DEFAULT now() relu
        assertThat(reloadedDoctor.isActive()).isTrue();
        assertThat(reloadedDoctor.getSpecialties()).extracting(Specialty::getLabel).containsExactly("Dermatologie");

        var reloadedPatient = em.find(Patient.class, patient.getId());
        assertThat(reloadedPatient.getRole()).isEqualTo(Role.PATIENT);
        assertThat(reloadedPatient.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 17));

        // Requête polymorphe : Hibernate retrouve la bonne sous-classe par jointure
        assertThat(em.find(UserAccount.class, patient.getId())).isInstanceOf(Patient.class);

        var hours = em.getEntityManager()
                .createQuery("select w from WorkingHours w where w.practitioner.id = :id", WorkingHours.class)
                .setParameter("id", doctor.getId())
                .getSingleResult();
        assertThat(hours.getDay()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(hours.getModality()).isEqualTo(Modality.PRESENTIEL);
        assertThat(hours.getLocation().getPostalCode()).isEqualTo("75017");

        var reloadedAppointment = em.find(Appointment.class, appointment.getId());
        assertThat(reloadedAppointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRME);
        assertThat(reloadedAppointment.getPractitioner().getId()).isEqualTo(doctor.getId());
        assertThat(reloadedAppointment.getEnd()).isEqualTo(Instant.parse("2026-11-03T08:30:00Z"));
        assertThat(reloadedAppointment.getCreatedAt()).isNotNull();
        assertThat(reloadedAppointment.getCancelledAt()).isNull();
    }

    @Test
    void dayOfWeekIsStoredAsIsoNumber() {
        var doctor = em.persist(new Practitioner("dr.b@example.com", "hash", "B", "B", "20202020202"));
        em.persist(new WorkingHours(doctor, DayOfWeek.SUNDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                Modality.DISTANCIEL, null, LocalDate.of(2026, 9, 1), null));
        em.flush();

        // Lecture SQL brute : vérifie la valeur réellement stockée, pas sa conversion Java
        Number stored = (Number) em.getEntityManager()
                .createNativeQuery("select jour_semaine from plage_horaire where praticien_id = :id")
                .setParameter("id", doctor.getId())
                .getSingleResult();
        assertThat(stored.intValue()).isEqualTo(7);
    }
}
