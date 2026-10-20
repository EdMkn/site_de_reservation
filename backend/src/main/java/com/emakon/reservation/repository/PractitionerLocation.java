package com.emakon.reservation.repository;

import com.emakon.reservation.domain.Location;

/** Projection : un lieu où exerce un praticien (résultat d'une requête "select new"). */
public record PractitionerLocation(Long practitionerId, Location location) {
}
