package com.emakon.reservation.api;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emakon.reservation.availability.Modality;
import com.emakon.reservation.domain.ConsultationType;
import com.emakon.reservation.domain.Location;
import com.emakon.reservation.domain.Practitioner;
import com.emakon.reservation.domain.Specialty;
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

/** Annuaire public : sans jeton, praticiens actifs, motifs actifs, lieux des plages en cours. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class PractitionerControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-01T08:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    private Practitioner martin;
    private Specialty dermatology;
    private ConsultationType consultation;

    @BeforeEach
    void setUp() {
        var generalPractice = new Specialty("Médecine générale");
        dermatology = new Specialty("Dermatologie");
        em.persist(generalPractice);
        em.persist(dermatology);
        var paris = new Location("Cabinet Paris", "1 rue A", "75001", "Paris");
        var lyon = new Location("Cabinet Lyon", "2 rue B", "69001", "Lyon");
        var lille = new Location("Ancien cabinet", "3 rue C", "59000", "Lille");
        em.persist(paris);
        em.persist(lyon);
        em.persist(lille);

        martin = new Practitioner("martin@example.com", "hash", "Martin", "Claire", "11111111111");
        martin.addSpecialty(generalPractice);
        var benali = new Practitioner("benali@example.com", "hash", "Benali", "Karim", "22222222222");
        benali.addSpecialty(dermatology);
        var retired = new Practitioner("retired@example.com", "hash", "Ancien", "Paul", "33333333333");
        retired.deactivate();
        em.persist(martin);
        em.persist(benali);
        em.persist(retired);

        consultation = new ConsultationType(martin, "Consultation", Duration.ofMinutes(20), true);
        var oldType = new ConsultationType(martin, "Ancien motif", Duration.ofMinutes(10), false);
        oldType.deactivate();
        em.persist(consultation);
        em.persist(oldType);

        // Martin : Paris et Lyon en cours, Lille terminé ; une plage en distanciel (sans lieu)
        LocalDate since = LocalDate.of(2026, 1, 1);
        em.persist(new WorkingHours(martin, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0),
                Modality.PRESENTIEL, paris, since, null));
        em.persist(new WorkingHours(martin, DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0),
                Modality.PRESENTIEL, lyon, since, null));
        em.persist(new WorkingHours(martin, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0),
                Modality.PRESENTIEL, lille, since, LocalDate.of(2026, 6, 30)));
        em.persist(new WorkingHours(martin, DayOfWeek.FRIDAY, LocalTime.of(14, 0), LocalTime.of(16, 0),
                Modality.DISTANCIEL, null, since, null));
        em.flush();
    }

    @Test
    void listsActivePractitionersWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/practitioners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].lastName").value(contains("Benali", "Martin")))
                .andExpect(jsonPath("$[1].specialties[0].label").value("Médecine générale"))
                .andExpect(jsonPath("$[1].cities").value(contains("Lyon", "Paris")))
                .andExpect(jsonPath("$[0].cities").isEmpty());
    }

    @Test
    void filtersBySpecialty() throws Exception {
        mvc.perform(get("/api/practitioners").param("specialtyId", dermatology.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].lastName").value(contains("Benali")));
    }

    @Test
    void detailListsActiveConsultationTypesAndCurrentLocations() throws Exception {
        mvc.perform(get("/api/practitioners/" + martin.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultationTypes[*].id").value(contains(consultation.getId().intValue())))
                .andExpect(jsonPath("$.consultationTypes[0].durationMinutes").value(20))
                .andExpect(jsonPath("$.consultationTypes[0].remoteAllowed").value(true))
                .andExpect(jsonPath("$.locations[*].name").value(contains("Cabinet Lyon", "Cabinet Paris")));
    }

    @Test
    void unknownPractitionerReturns404() throws Exception {
        mvc.perform(get("/api/practitioners/999999")).andExpect(status().isNotFound());
    }

    @Test
    void listsSpecialties() throws Exception {
        mvc.perform(get("/api/specialties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].label").value(contains("Dermatologie", "Médecine générale")));
    }
}
