package com.emakon.reservation.availability;

/**
 * Modalité de consultation. Mêmes valeurs que le type SQL {@code modalite}.
 * Enum Java pur : les entités JPA peuvent la réutiliser sans que ce package dépende de JPA.
 */
public enum Modality {
    PRESENTIEL,
    DISTANCIEL
}
