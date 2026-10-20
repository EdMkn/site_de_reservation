package com.emakon.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionTest {

    private static final Duration IDLE = Duration.ofDays(7);
    private static final Duration MAX = Duration.ofDays(30);
    private static final Instant LOGIN = Instant.parse("2026-10-01T08:00:00Z");

    private static Session newSession() {
        var user = new Patient("p@example.com", "hash", "Dupont", "Jean", LocalDate.of(1990, 1, 1));
        return new Session(user, "hash", LOGIN, IDLE, MAX);
    }

    @Test
    @DisplayName("sans activité, la session expire après la durée d'inactivité")
    void expiresWhenIdle() {
        var session = newSession();

        assertThat(session.getExpiresAt()).isEqualTo(LOGIN.plus(IDLE));
        assertThat(session.isValidAt(LOGIN.plus(IDLE).minusSeconds(1), MAX)).isTrue();
        assertThat(session.isValidAt(LOGIN.plus(IDLE), MAX)).isFalse();
    }

    @Test
    @DisplayName("chaque utilisation repousse l'expiration (glissante)")
    void slidingExpiration() {
        var session = newSession();
        Instant use = LOGIN.plus(Duration.ofDays(5));

        session.extend(use, IDLE, MAX);

        assertThat(session.getExpiresAt()).isEqualTo(use.plus(IDLE));
        assertThat(session.isValidAt(LOGIN.plus(Duration.ofDays(10)), MAX)).isTrue();
    }

    @Test
    @DisplayName("même utilisée chaque jour, la session meurt 30 jours après la connexion")
    void absoluteLimit() {
        var session = newSession();
        for (int day = 1; day < 30; day++) {
            session.extend(LOGIN.plus(Duration.ofDays(day)), IDLE, MAX);
        }

        assertThat(session.getExpiresAt()).isEqualTo(LOGIN.plus(MAX));
        assertThat(session.isValidAt(LOGIN.plus(MAX), MAX)).isFalse();
    }
}
