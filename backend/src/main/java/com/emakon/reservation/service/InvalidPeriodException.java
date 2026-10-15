package com.emakon.reservation.service;

/** Période de recherche invalide (inversée ou trop longue). Traduite en HTTP 400. */
public class InvalidPeriodException extends RuntimeException {

    public InvalidPeriodException(String message) {
        super(message);
    }
}
