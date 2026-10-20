package com.emakon.reservation.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Pas de @Email ni de règle de longueur sur le mot de passe : un format invalide est
 * simplement un mauvais identifiant (401), inutile de donner un indice avec un 400 détaillé.
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
