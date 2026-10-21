package com.emakon.reservation.api;

import com.emakon.reservation.service.AppointmentService;
import com.emakon.reservation.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.emakon.reservation.service.InvalidPeriodException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final ZoneId zone;

    public AppointmentController(AppointmentService appointmentService, ZoneId practiceZone) {
        this.appointmentService = appointmentService;
        this.zone = practiceZone;
    }

    @Operation(summary = "Mes rendez-vous (patient) ou mon agenda (praticien), tous statuts",
            description = "Sans paramètres : tous les rendez-vous. Avec from et to (ensemble) : ceux qui "
                    + "commencent entre ces deux jours inclus, 31 jours au plus.")
    @ApiResponse(responseCode = "400", description = "Une seule borne, période inversée ou trop longue")
    @GetMapping("/api/appointments")
    public List<AppointmentResponse> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Premier jour, inclus", example = "2026-11-02")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Dernier jour, inclus", example = "2026-11-08")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if ((from == null) != (to == null)) {
            throw new InvalidPeriodException("Les paramètres from et to vont ensemble");
        }
        var views = from == null ? appointmentService.listFor(user) : appointmentService.listFor(user, from, to);
        return views.stream().map(v -> AppointmentResponse.from(v, zone)).toList();
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
