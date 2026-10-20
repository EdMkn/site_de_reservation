package com.emakon.reservation.api;

import com.emakon.reservation.availability.Modality;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Corps de POST /api/appointments.
 *
 * Bean Validation ne vérifie que la forme (champs présents, longueur) : 400 si elle est
 * invalide. Le fond (créneau réservable, motif du praticien...) relève du service.
 *
 * @param locationId null en distanciel
 */
public record BookingRequest(
        @Schema(description = "PROVISOIRE : sera remplacé par l'utilisateur authentifié (étape 6)")
        @NotNull Long patientId,
        @NotNull Long practitionerId,
        @NotNull Long consultationTypeId,
        @NotNull Modality modality,
        Long locationId,
        @Schema(description = "Début du créneau, tel que renvoyé par le endpoint des disponibilités",
                example = "2026-11-02T10:00:00+01:00")
        @NotNull OffsetDateTime start,
        @Size(max = 1000) String patientNote) {
}
