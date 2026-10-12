package com.emakon.reservation.availability;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Règle récurrente du planning (une ligne de plage_horaire), en heure locale du cabinet.
 * Ex. : le mardi de 9 h à 12 h, en présentiel au lieu 3, à partir du 1er septembre.
 *
 * @param locationId obligatoire en présentiel, null en distanciel (même règle que le CHECK SQL)
 * @param validUntil null = sans limite ; sinon dernier jour inclus
 */
public record ScheduleRule(
        DayOfWeek day,
        LocalTime start,
        LocalTime end,
        Modality modality,
        Long locationId,
        LocalDate validFrom,
        LocalDate validUntil) {

    public ScheduleRule {
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(modality, "modality");
        Objects.requireNonNull(validFrom, "validFrom");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end doit être après start");
        }
        if ((modality == Modality.PRESENTIEL) != (locationId != null)) {
            throw new IllegalArgumentException("lieu obligatoire en présentiel, interdit en distanciel");
        }
        if (validUntil != null && validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("validUntil ne peut pas précéder validFrom");
        }
    }

    /** La règle s'applique-t-elle à cette date (bon jour de la semaine et dans la période de validité) ? */
    boolean appliesTo(LocalDate date) {
        return date.getDayOfWeek() == day
                && !date.isBefore(validFrom)
                && (validUntil == null || !date.isAfter(validUntil));
    }
}
