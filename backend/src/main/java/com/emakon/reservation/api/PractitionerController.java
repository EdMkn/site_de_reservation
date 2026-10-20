package com.emakon.reservation.api;

import com.emakon.reservation.service.PractitionerService;
import com.emakon.reservation.service.PractitionerViews.PractitionerDetail;
import com.emakon.reservation.service.PractitionerViews.PractitionerSummary;
import com.emakon.reservation.service.PractitionerViews.SpecialtyView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Annuaire public des praticiens : accessible sans être connecté, comme les créneaux. */
@RestController
public class PractitionerController {

    private final PractitionerService practitionerService;

    public PractitionerController(PractitionerService practitionerService) {
        this.practitionerService = practitionerService;
    }

    @Operation(summary = "Spécialités, pour le filtre de l'annuaire")
    @GetMapping("/api/specialties")
    public List<SpecialtyView> specialties() {
        return practitionerService.listSpecialties();
    }

    @Operation(summary = "Praticiens actifs, éventuellement filtrés par spécialité")
    @GetMapping("/api/practitioners")
    public List<PractitionerSummary> list(
            @Parameter(description = "Identifiant de spécialité") @RequestParam(required = false) Integer specialtyId) {
        return practitionerService.list(specialtyId);
    }

    @Operation(summary = "Fiche d'un praticien : spécialités, motifs proposés, lieux d'exercice")
    @GetMapping("/api/practitioners/{id}")
    public PractitionerDetail get(@PathVariable Long id) {
        return practitionerService.get(id);
    }
}
