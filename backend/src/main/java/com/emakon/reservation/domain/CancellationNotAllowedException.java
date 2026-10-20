package com.emakon.reservation.domain;

/**
 * Règle d'annulation non respectée (statut, rendez-vous passé, délai de 24 h). Traduite
 * en HTTP 409. Dans le package domain, car c'est l'entité qui la lève.
 */
public class CancellationNotAllowedException extends RuntimeException {

    public CancellationNotAllowedException(String message) {
        super(message);
    }
}
