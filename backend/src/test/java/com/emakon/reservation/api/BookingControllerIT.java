package com.emakon.reservation.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Couche HTTP de la réservation : JSON accepté, codes de statut et format des erreurs.
 * La concurrence est testée à part (BookingConcurrencyIT), sans @Transactional.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class BookingControllerIT {

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

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    private Location cabinet;
    private Practitioner doctor;
    private ConsultationType consultation;
    private Patient patient;

    @BeforeEach
    void setUp() {
        cabinet = new Location("Cabinet", "1 rue A", "75001", "Paris");
        em.persist(cabinet);
        doctor = new Practitioner("dr@example.com", "hash", "Martin", "Claire", "11111111111");
        em.persist(doctor);
        consultation = new ConsultationType(doctor, "Consultation", Duration.ofMinutes(30), false);
        em.persist(consultation);
        patient = new Patient("p@example.com", "hash", "Dupont", "Jean", LocalDate.of(1990, 1, 1));
        em.persist(patient);
        em.persist(new WorkingHours(doctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0),
                Modality.PRESENTIEL, cabinet, LocalDate.of(2026, 1, 1), null));
        em.flush();
    }

    private ResultActions book(Long patientId, Long consultationTypeId, String start) throws Exception {
        String body = """
                {"patientId": %d, "practitionerId": %d, "consultationTypeId": %d,
                 "modality": "PRESENTIEL", "locationId": %d, "start": "%s"}
                """.formatted(patientId, doctor.getId(), consultationTypeId, cabinet.getId(), start);
        return mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void bookingAFreeSlotReturns201WithTheId() throws Exception {
        book(patient.getId(), consultation.getId(), "2026-11-02T10:00:00+01:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").isNumber());
    }

    @Test
    void bookingATakenSlotReturns409SlotUnavailable() throws Exception {
        book(patient.getId(), consultation.getId(), "2026-11-02T10:00:00+01:00").andExpect(status().isCreated());

        var other = new Patient("p2@example.com", "hash", "Diallo", "Amina", LocalDate.of(1992, 2, 2));
        em.persist(other);
        book(other.getId(), consultation.getId(), "2026-11-02T10:00:00+01:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/slot-unavailable"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void startOutsideTheProposedSlotsReturns409() throws Exception {
        // 10h10 : dans la plage, mais pas aligné sur le découpage en créneaux de 30 min
        book(patient.getId(), consultation.getId(), "2026-11-02T10:10:00+01:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/slot-unavailable"));
    }

    @Test
    void unknownPatientReturns404() throws Exception {
        book(999_999L, consultation.getId(), "2026-11-02T10:00:00+01:00")
                .andExpect(status().isNotFound());
    }

    @Test
    void missingFieldReturns400() throws Exception {
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
