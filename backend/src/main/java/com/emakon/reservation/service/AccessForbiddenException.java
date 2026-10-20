package com.emakon.reservation.service;

/**
 * L'utilisateur est connecté mais n'a pas le droit d'accéder à cette ressource (par
 * exemple le rendez-vous d'un autre patient). Traduite en HTTP 403.
 *
 * Nommée ainsi pour ne pas être confondue avec AccessDeniedException de Spring Security.
 */
public class AccessForbiddenException extends RuntimeException {

    public AccessForbiddenException(String message) {
        super(message);
    }
}
