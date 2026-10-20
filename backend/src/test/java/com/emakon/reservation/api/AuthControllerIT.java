package com.emakon.reservation.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emakon.reservation.domain.Patient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class AuthControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper mapper;

    private Patient patient;

    @BeforeEach
    void setUp() {
        patient = new Patient("jean@example.com", passwordEncoder.encode("secret"), "Dupont", "Jean",
                LocalDate.of(1990, 1, 1));
        em.persist(patient);
        em.flush();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(email, password))));
    }

    private String token() throws Exception {
        String json = login("jean@example.com", "secret").andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("token").asText();
    }

    private ResultActions me(String token) throws Exception {
        return mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void loginReturnsATokenThatIdentifiesTheUser() throws Exception {
        login("jean@example.com", "secret")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.role").value("PATIENT"));

        me(token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient.getId()))
                .andExpect(jsonPath("$.email").value("jean@example.com"));
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        login("Jean@Example.com", "secret").andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        login("jean@example.com", "wrong")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email ou mot de passe incorrect"));
        login("nobody@example.com", "secret")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email ou mot de passe incorrect"));
    }

    @Test
    void protectedRouteWithoutValidTokenReturns401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
        me("not-a-real-token").andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesTheToken() throws Exception {
        String token = token();

        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        me(token).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedAccountCanNoLongerUseItsToken() throws Exception {
        String token = token();
        patient.deactivate();
        em.flush();

        me(token).andExpect(status().isUnauthorized());
        login("jean@example.com", "secret").andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRouteIsDenied() throws Exception {
        mvc.perform(get("/not-an-api-route").header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isForbidden());
    }
}
