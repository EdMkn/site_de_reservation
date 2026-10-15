package com.emakon.reservation.service;

/** Ressource demandée inexistante (ou inaccessible dans ce contexte). Traduite en HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
