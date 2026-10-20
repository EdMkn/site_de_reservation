package com.emakon.reservation.service;

import java.time.Instant;

/**
 * @param token jeton en clair : renvoyé une seule fois au client, jamais stocké
 */
public record LoginResult(String token, Instant expiresAt, AuthenticatedUser user) {
}
