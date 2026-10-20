package com.emakon.reservation.api;

import com.emakon.reservation.domain.Role;

public record CurrentUserResponse(Long id, String email, Role role) {
}
