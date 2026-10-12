package com.emakon.reservation.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeRangeTest {

    private static TimeRange range(String start, String end) {
        return new TimeRange(Instant.parse("2026-11-02T" + start + ":00Z"), Instant.parse("2026-11-02T" + end + ":00Z"));
    }

    @Test
    @DisplayName("deux intervalles disjoints ne se chevauchent pas")
    void disjoint() {
        assertThat(range("09:00", "09:30").overlaps(range("10:00", "10:30"))).isFalse();
    }

    @Test
    @DisplayName("deux intervalles qui se touchent ne se chevauchent pas (fin exclue), dans les deux sens")
    void adjacent() {
        assertThat(range("10:00", "10:30").overlaps(range("10:30", "11:00"))).isFalse();
        assertThat(range("10:30", "11:00").overlaps(range("10:00", "10:30"))).isFalse();
    }

    @Test
    @DisplayName("un chevauchement partiel est détecté, dans les deux sens")
    void partial() {
        assertThat(range("10:00", "10:30").overlaps(range("10:15", "10:45"))).isTrue();
        assertThat(range("10:15", "10:45").overlaps(range("10:00", "10:30"))).isTrue();
    }

    @Test
    @DisplayName("un intervalle inclus dans l'autre chevauche, dans les deux sens")
    void contained() {
        assertThat(range("09:00", "12:00").overlaps(range("10:00", "10:30"))).isTrue();
        assertThat(range("10:00", "10:30").overlaps(range("09:00", "12:00"))).isTrue();
    }

    @Test
    @DisplayName("deux intervalles identiques se chevauchent")
    void identical() {
        assertThat(range("10:00", "10:30").overlaps(range("10:00", "10:30"))).isTrue();
    }

    @Test
    @DisplayName("un intervalle vide ou inversé est refusé")
    void invalid() {
        assertThatThrownBy(() -> range("10:00", "10:00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> range("10:30", "10:00")).isInstanceOf(IllegalArgumentException.class);
    }
}
