package com.emakon.reservation.availability;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Ce que demande le patient : une période et un motif.
 *
 * @param from          premier jour, inclus
 * @param to            dernier jour, inclus
 * @param duration      durée du motif (taille des créneaux)
 * @param remoteAllowed le motif autorise-t-il la téléconsultation ?
 */
public record AvailabilityQuery(LocalDate from, LocalDate to, Duration duration, boolean remoteAllowed) {

    public AvailabilityQuery {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(duration, "duration");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to ne peut pas précéder from");
        }
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("la durée doit être strictement positive");
        }
    }
}
