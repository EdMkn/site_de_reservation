package com.emakon.reservation.service;

/**
 * Email inconnu, mot de passe faux ou compte désactivé : un seul cas, un seul message,
 * pour ne pas révéler quels emails ont un compte. Traduite en HTTP 401.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email ou mot de passe incorrect");
    }
}
