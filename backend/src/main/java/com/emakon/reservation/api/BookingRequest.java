package com.emakon.reservation.api;

import com.emakon.reservation.availability.Modality;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Corps de POST /api/appointments. Pas de patientId : le patient est l'utilisateur
 * connecté, déduit du jeton. Le client ne peut donc pas réserver au nom d'un autre.
 *
 * Bean Validation ne vérifie que la forme (champs présents, longueur) : 400 si elle est
 * invalide. Le fond (créneau réservable, motif du praticien...) relève du service.
 *
 * @param locationId null en distanciel
 */
public record BookingRequest(
        @NotNull Long practitionerId,
        @NotNull Long consultationTypeId,
        @NotNull Modality modality,
        Long locationId,
        @Schema(description = "Début du créneau, tel que renvoyé par le endpoint des disponibilités",
                example = "2026-11-02T10:00:00+01:00")
        @NotNull OffsetDateTime start,
        @Size(max = 1000) String patientNote) {
}
