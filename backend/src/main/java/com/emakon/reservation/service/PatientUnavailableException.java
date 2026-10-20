package com.emakon.reservation.service;

/** Le patient a déjà un rendez-vous confirmé qui chevauche le créneau demandé. Traduite en HTTP 409. */
public class PatientUnavailableException extends RuntimeException {

    public PatientUnavailableException(String message) {
        super(message);
    }
}
