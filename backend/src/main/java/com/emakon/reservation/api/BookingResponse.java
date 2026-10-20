package com.emakon.reservation.api;

/** Réponse à une réservation réussie (HTTP 201). */
public record BookingResponse(Long appointmentId) {
}
