package com.emakon.reservation.domain;

/**
 * Cycle de vie d'un rendez-vous. Mêmes libellés que le type SQL {@code statut_rdv}.
 * Seul CONFIRME bloque l'agenda (contraintes d'exclusion de la table rendez_vous).
 */
public enum AppointmentStatus {
    CONFIRME,
    ANNULE_PATIENT,
    ANNULE_PRATICIEN,
    HONORE,
    ABSENT
}
