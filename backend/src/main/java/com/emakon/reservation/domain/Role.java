package com.emakon.reservation.domain;

/**
 * Rôle d'un utilisateur. Les noms des constantes doivent être identiques aux libellés
 * du type SQL {@code role_utilisateur} : c'est le nom qui est stocké (EnumType.STRING).
 */
public enum Role {
    PATIENT,
    PRATICIEN
}
