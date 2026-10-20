package com.emakon.reservation.api;

import com.emakon.reservation.service.AuthService;
import com.emakon.reservation.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;
    private final ZoneId zone;

    public AuthController(AuthService authService, ZoneId practiceZone) {
        this.authService = authService;
        this.zone = practiceZone;
    }

    @Operation(summary = "Connexion : renvoie un jeton à passer dans l'en-tête Authorization: Bearer")
    @ApiResponse(responseCode = "200", description = "Connecté")
    @ApiResponse(responseCode = "401", description = "Email ou mot de passe incorrect")
    @PostMapping("/api/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var result = authService.login(request.email(), request.password());
        return new LoginResponse(result.token(), result.expiresAt().atZone(zone).toOffsetDateTime(),
                result.user().id(), result.user().role());
    }

    @Operation(summary = "Déconnexion : le jeton est immédiatement invalidé")
    @ApiResponse(responseCode = "204", description = "Déconnecté")
    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        authService.logout(user.sessionId());
    }

    @Operation(summary = "Utilisateur connecté")
    @GetMapping("/api/auth/me")
    public CurrentUserResponse me(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return new CurrentUserResponse(user.id(), user.email(), user.role());
    }
}
