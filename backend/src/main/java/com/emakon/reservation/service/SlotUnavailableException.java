package com.emakon.reservation.service;

/**
 * Le créneau demandé n'est pas (ou plus) réservable : déjà pris, hors planning, passé ou
 * incompatible avec le motif. Traduite en HTTP 409.
 */
public class SlotUnavailableException extends RuntimeException {

    public SlotUnavailableException(String message) {
        super(message);
    }
}
