package com.emakon.reservation.api;

import com.emakon.reservation.domain.Role;
import java.time.OffsetDateTime;

/**
 * @param token     à renvoyer dans l'en-tête "Authorization: Bearer <token>"
 * @param expiresAt expiration glissante : repoussée à chaque requête
 */
public record LoginResponse(String token, OffsetDateTime expiresAt, Long userId, Role role) {
}
