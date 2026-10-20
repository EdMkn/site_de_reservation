package com.emakon.reservation.api;

import com.emakon.reservation.service.AppointmentService;
import com.emakon.reservation.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.ZoneId;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final ZoneId zone;

    public AppointmentController(AppointmentService appointmentService, ZoneId practiceZone) {
        this.appointmentService = appointmentService;
        this.zone = practiceZone;
    }

    @Operation(summary = "Mes rendez-vous (patient) ou mon agenda (praticien), tous statuts")
    @GetMapping("/api/appointments")
    public List<AppointmentResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return appointmentService.listFor(user).stream()
                .map(v -> AppointmentResponse.from(v, zone))
                .toList();
    }

    @Operation(summary = "Détail d'un rendez-vous")
    @ApiResponse(responseCode = "403", description = "Rendez-vous d'un autre utilisateur")
    @ApiResponse(responseCode = "404", description = "Rendez-vous introuvable")
    @GetMapping("/api/appointments/{id}")
    public AppointmentResponse get(@PathVariable Long id,
                                   @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return AppointmentResponse.from(appointmentService.get(id, user), zone);
    }

    /**
     * POST sur une sous-ressource "cancel" plutôt que DELETE : le rendez-vous n'est pas
     * supprimé, il change de statut et reste consultable.
     */
    @Operation(summary = "Annuler un rendez-vous (patient : au moins 24 h avant ; praticien : avant le début)")
    @ApiResponse(responseCode = "403", description = "Rendez-vous d'un autre utilisateur")
    @ApiResponse(responseCode = "404", description = "Rendez-vous introuvable")
    @ApiResponse(responseCode = "409", description = "Annulation impossible (type /problems/cancellation-not-allowed)")
    @PostMapping("/api/appointments/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable Long id,
                                      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return AppointmentResponse.from(appointmentService.cancel(id, user), zone);
    }
}
