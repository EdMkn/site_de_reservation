package com.emakon.reservation.api;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emakon.reservation.availability.Modality;
import com.emakon.reservation.domain.Appointment;
import com.emakon.reservation.domain.ConsultationType;
import com.emakon.reservation.domain.Location;
import com.emakon.reservation.domain.Patient;
import com.emakon.reservation.domain.Practitioner;
import com.emakon.reservation.service.AuthService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Règles d'autorisation sur les rendez-vous : qui voit, qui annule. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class AppointmentControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired AuthService authService;
    @Autowired PasswordEncoder passwordEncoder;

    private Appointment jeanInAMonth;     // Jean chez Martin, dans un mois
    private Appointment jeanInTwoHours;   // Jean chez Martin, dans 2 h
    private Appointment aminaWithBenali;  // Amina chez Benali

    private String jean;
    private String amina;
    private String martin;
    private String benali;

    @BeforeEach
    void setUp() {
        String hash = passwordEncoder.encode("secret");
        var cabinet = new Location("Cabinet", "1 rue A", "75001", "Paris");
        em.persist(cabinet);
        var drMartin = new Practitioner("martin@example.com", hash, "Martin", "Claire", "11111111111");
        var drBenali = new Practitioner("benali@example.com", hash, "Benali", "Karim", "22222222222");
        var pJean = new Patient("jean@example.com", hash, "Dupont", "Jean", LocalDate.of(1990, 1, 1));
        var pAmina = new Patient("amina@example.com", hash, "Diallo", "Amina", LocalDate.of(1992, 2, 2));
        em.persist(drMartin);
        em.persist(drBenali);
        em.persist(pJean);
        em.persist(pAmina);
        var martinType = new ConsultationType(drMartin, "Consultation", Duration.ofMinutes(30), false);
        var benaliType = new ConsultationType(drBenali, "Dermatologie", Duration.ofMinutes(30), false);
        em.persist(martinType);
        em.persist(benaliType);

        jeanInTwoHours = new Appointment(pJean, martinType, Modality.PRESENTIEL, cabinet, NOW.plus(Duration.ofHours(2)), null);
        jeanInAMonth = new Appointment(pJean, martinType, Modality.PRESENTIEL, cabinet, NOW.plus(Duration.ofDays(30)), null);
        aminaWithBenali = new Appointment(pAmina, benaliType, Modality.PRESENTIEL, cabinet, NOW.plus(Duration.ofDays(30)), null);
        em.persist(jeanInTwoHours);
        em.persist(jeanInAMonth);
        em.persist(aminaWithBenali);
        em.flush();

        jean = authService.login("jean@example.com", "secret").token();
        amina = authService.login("amina@example.com", "secret").token();
        martin = authService.login("martin@example.com", "secret").token();
        benali = authService.login("benali@example.com", "secret").token();
    }

    private ResultActions getAs(String token, String url) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions cancelAs(String token, Appointment appointment) throws Exception {
        return mvc.perform(post("/api/appointments/" + appointment.getId() + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void patientListsOnlyHisAppointments() throws Exception {
        getAs(jean, "/api/appointments")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(
                        jeanInTwoHours.getId().intValue(), jeanInAMonth.getId().intValue())));
    }

    @Test
    void practitionerListsOnlyHisAgenda() throws Exception {
        getAs(benali, "/api/appointments")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(aminaWithBenali.getId().intValue())))
                .andExpect(jsonPath("$[0].patientName").value("Amina Diallo"));
    }

    @Test
    void participantsCanSeeTheAppointment() throws Exception {
        getAs(jean, "/api/appointments/" + jeanInAMonth.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.practitionerName").value("Claire Martin"))
                .andExpect(jsonPath("$.status").value("CONFIRME"));
        getAs(martin, "/api/appointments/" + jeanInAMonth.getId()).andExpect(status().isOk());
    }

    @Test
    void othersGet403() throws Exception {
        getAs(amina, "/api/appointments/" + jeanInAMonth.getId()).andExpect(status().isForbidden());
        getAs(benali, "/api/appointments/" + jeanInAMonth.getId()).andExpect(status().isForbidden());
        cancelAs(amina, jeanInAMonth).andExpect(status().isForbidden());
    }

    @Test
    void unknownAppointmentReturns404() throws Exception {
        getAs(jean, "/api/appointments/999999").andExpect(status().isNotFound());
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/appointments")).andExpect(status().isUnauthorized());
    }

    @Test
    void patientCancelsMoreThan24hBefore() throws Exception {
        cancelAs(jean, jeanInAMonth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANNULE_PATIENT"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
    }

    @Test
    void patientCannotCancelLessThan24hBefore() throws Exception {
        cancelAs(jean, jeanInTwoHours)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/cancellation-not-allowed"));
    }

    @Test
    void practitionerCanCancelLessThan24hBefore() throws Exception {
        cancelAs(martin, jeanInTwoHours)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANNULE_PRATICIEN"));
    }

    @Test
    void cancellationIsPersisted() throws Exception {
        cancelAs(jean, jeanInAMonth).andExpect(status().isOk());
        em.flush();
        em.clear();

        // Relu en base : la contrainte chk_rendez_vous_annulation a accepté l'UPDATE
        getAs(martin, "/api/appointments/" + jeanInAMonth.getId())
                .andExpect(jsonPath("$.status").value("ANNULE_PATIENT"));
        cancelAs(martin, jeanInAMonth).andExpect(status().isConflict());
    }
}
