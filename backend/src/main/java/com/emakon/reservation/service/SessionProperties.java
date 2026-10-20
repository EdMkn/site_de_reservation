package com.emakon.reservation.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Durées de vie des sessions (application.yml, préfixe reservation.session).
 *
 * @param idleTimeout inactivité maximale : l'expiration glissante
 * @param maxLifetime durée maximale depuis la connexion : l'expiration absolue
 */
@ConfigurationProperties("reservation.session")
public record SessionProperties(Duration idleTimeout, Duration maxLifetime) {
}
