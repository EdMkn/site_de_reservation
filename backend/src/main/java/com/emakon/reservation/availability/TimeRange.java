package com.emakon.reservation.availability;

import java.time.Instant;
import java.util.Objects;

/**
 * Intervalle de temps semi-ouvert [start, end) : le début est inclus, la fin exclue.
 * Même convention que tstzrange(debut, fin, '[)') dans la contrainte d'exclusion SQL,
 * pour que le calcul et la base soient d'accord sur ce qu'est un chevauchement.
 */
public record TimeRange(Instant start, Instant end) {

    public TimeRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end doit être strictement après start : " + start + " / " + end);
        }
    }

    /**
     * Deux intervalles [a, b) et [c, d) se chevauchent si et seulement si a < d et c < b.
     * Avec des fins exclues, 10:00–10:30 et 10:30–11:00 ne se chevauchent pas.
     */
    public boolean overlaps(TimeRange other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
