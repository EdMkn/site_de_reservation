package com.emakon.reservation.service;

import com.emakon.reservation.domain.Role;

/**
 * Utilisateur à l'origine de la requête, déduit du jeton (jamais du corps de la requête).
 * Placé par le filtre d'authentification dans le SecurityContext ; les contrôleurs le
 * reçoivent avec @AuthenticationPrincipal et le transmettent aux services, qui s'en
 * servent pour les règles d'autorisation.
 *
 * Un record, et non l'entité UserAccount : il vit le temps de la requête, hors de toute
 * session Hibernate, et ne porte que le nécessaire.
 *
 * @param sessionId pour la déconnexion
 */
public record AuthenticatedUser(Long id, Long sessionId, String email, Role role) {
}
