package com.emakon.reservation.availability;

import java.time.Instant;
import java.util.Objects;

/**
 * Créneau libre proposé au patient : quand, et où (modalité + lieu).
 *
 * @param locationId null en distanciel
 */
public record Slot(Instant start, Instant end, Modality modality, Long locationId) {

    public Slot {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(modality, "modality");
    }

    TimeRange range() {
        return new TimeRange(start, end);
    }
}
