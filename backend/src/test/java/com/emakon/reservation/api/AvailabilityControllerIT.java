package com.emakon.reservation.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emakon.reservation.availability.Modality;
import com.emakon.reservation.domain.Absence;
import com.emakon.reservation.domain.Appointment;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test de bout en bout du endpoint : requête HTTP (MockMvc) -> sécurité -> contrôleur ->
 * service -> repositories -> PostgreSQL réel -> JSON.
 *
 * @Transactional : les données insérées par le test et la requête MockMvc (exécutée dans
 * le même thread) partagent la transaction, annulée à la fin de chaque test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class AvailabilityControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Horloge figée bien avant les dates testées : aucun créneau n'est "passé". */
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    private Practitioner doctor;
    private ConsultationType consultation;

    @BeforeEach
    void setUp() {
        var cabinet = new Location("Cabinet", "1 rue A", "75001", "Paris");
        em.persist(cabinet);
        doctor = new Practitioner("dr@example.com", "hash", "Martin", "Claire", "11111111111");
        em.persist(doctor);
        consultation = new ConsultationType(doctor, "Consultation", Duration.ofMinutes(30), false);
        em.persist(consultation);
        var patient = new Patient("p@example.com", "hash", "Dupont", "Jean", LocalDate.of(1990, 1, 1));
        em.persist(patient);

        // Lundi 9h–12h ; rendez-vous à 10h ; absence de 11h à 12h (heure de Paris, UTC+1 en novembre)
        em.persist(new WorkingHours(doctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0),
                Modality.PRESENTIEL, cabinet, LocalDate.of(2026, 1, 1), null));
        em.persist(new Appointment(patient, consultation, Modality.PRESENTIEL, cabinet,
                Instant.parse("2026-11-02T09:00:00Z"), null));
        em.persist(new Absence(doctor, Instant.parse("2026-11-02T10:00:00Z"), Instant.parse("2026-11-02T11:00:00Z"), null));
        em.flush();
    }

    private String url(Long practitionerId) {
        return "/api/practitioners/" + practitionerId + "/availability";
    }

    @Test
    void returnsFreeSlotsWithoutAuthentication() throws Exception {
        mvc.perform(get(url(doctor.getId()))
                        .param("consultationTypeId", consultation.getId().toString())
                        .param("from", "2026-11-02")
                        .param("to", "2026-11-08"))
                .andExpect(status().isOk())
                // 9h–12h en 30 min, moins 10:00 (rendez-vous) et 11:00/11:30 (absence)
                .andExpect(jsonPath("$[*].start").value(contains(
                        "2026-11-02T09:00:00+01:00", "2026-11-02T09:30:00+01:00", "2026-11-02T10:30:00+01:00")))
                .andExpect(jsonPath("$[0].end").value("2026-11-02T09:30:00+01:00"))
                .andExpect(jsonPath("$[0].modality").value("PRESENTIEL"));
    }

    @Test
    void unknownPractitionerReturns404() throws Exception {
        mvc.perform(get(url(999_999L))
                        .param("consultationTypeId", consultation.getId().toString())
                        .param("from", "2026-11-02")
                        .param("to", "2026-11-08"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("Praticien introuvable")));
    }

    @Test
    void consultationTypeOfAnotherPractitionerReturns404() throws Exception {
        var other = new Practitioner("dr2@example.com", "hash", "B", "B", "22222222222");
        em.persist(other);
        var otherType = new ConsultationType(other, "Autre", Duration.ofMinutes(20), false);
        em.persist(otherType);

        mvc.perform(get(url(doctor.getId()))
                        .param("consultationTypeId", otherType.getId().toString())
                        .param("from", "2026-11-02")
                        .param("to", "2026-11-08"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invertedPeriodReturns400() throws Exception {
        mvc.perform(get(url(doctor.getId()))
                        .param("consultationTypeId", consultation.getId().toString())
                        .param("from", "2026-11-08")
                        .param("to", "2026-11-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void missingOrMalformedParametersReturn400() throws Exception {
        mvc.perform(get(url(doctor.getId())).param("from", "2026-11-02").param("to", "2026-11-08"))
                .andExpect(status().isBadRequest());

        mvc.perform(get(url(doctor.getId()))
                        .param("consultationTypeId", consultation.getId().toString())
                        .param("from", "02/11/2026")
                        .param("to", "2026-11-08"))
                .andExpect(status().isBadRequest());
    }
}
