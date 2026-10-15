package com.emakon.reservation.api;

import com.emakon.reservation.availability.Modality;
import com.emakon.reservation.availability.Slot;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Créneau tel qu'exposé par l'API.
 *
 * Dates en OffsetDateTime dans le fuseau du cabinet ("2026-11-02T09:00:00+01:00") :
 * le frontend peut afficher l'heure locale directement, et l'instant reste non ambigu
 * grâce au décalage.
 *
 * @param locationId null en distanciel
 */
public record SlotResponse(OffsetDateTime start, OffsetDateTime end, Modality modality, Long locationId) {

    static SlotResponse from(Slot slot, ZoneId zone) {
        return new SlotResponse(
                slot.start().atZone(zone).toOffsetDateTime(),
                slot.end().atZone(zone).toOffsetDateTime(),
                slot.modality(),
                slot.locationId());
    }
}
