package com.emakon.reservation.api;

import com.emakon.reservation.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le contrôleur ne contient aucune logique : il lit la requête HTTP, appelle le service,
 * et convertit le résultat en DTO. Pas de @Transactional ici (convention du projet).
 */
@RestController
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final ZoneId zone;

    public AvailabilityController(AvailabilityService availabilityService, ZoneId practiceZone) {
        this.availabilityService = availabilityService;
        this.zone = practiceZone;
    }

    @Operation(summary = "Créneaux libres d'un praticien pour un motif, sur une période (31 jours maximum)")
    @GetMapping("/api/practitioners/{practitionerId}/availability")
    public List<SlotResponse> getAvailability(
            @PathVariable Long practitionerId,
            @Parameter(description = "Identifiant du motif de consultation")
            @RequestParam Long consultationTypeId,
            @Parameter(description = "Premier jour, inclus (AAAA-MM-JJ)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Dernier jour, inclus (AAAA-MM-JJ)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return availabilityService.findAvailableSlots(practitionerId, consultationTypeId, from, to).stream()
                .map(slot -> SlotResponse.from(slot, zone))
                .toList();
    }
}
