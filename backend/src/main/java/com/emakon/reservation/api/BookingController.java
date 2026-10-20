package com.emakon.reservation.api;

import com.emakon.reservation.service.AuthenticatedUser;
import com.emakon.reservation.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(summary = "Réserver un créneau")
    @ApiResponse(responseCode = "201", description = "Rendez-vous créé")
    @ApiResponse(responseCode = "400", description = "Requête mal formée")
    @ApiResponse(responseCode = "401", description = "Non connecté")
    @ApiResponse(responseCode = "403", description = "Connecté, mais pas en tant que patient")
    @ApiResponse(responseCode = "404", description = "Praticien ou motif introuvable")
    @ApiResponse(responseCode = "409", description = "Créneau indisponible (type /problems/slot-unavailable) "
            + "ou patient déjà occupé (type /problems/patient-unavailable)")
    @PostMapping("/api/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse book(@Valid @RequestBody BookingRequest request,
                                @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser patient) {
        // SecurityConfig n'ouvre cette route qu'au rôle PATIENT : patient.id() est bien un patient.
        Long id = bookingService.book(patient.id(), request.practitionerId(), request.consultationTypeId(),
                request.modality(), request.locationId(), request.start().toInstant(), request.patientNote());
        return new BookingResponse(id);
    }
}
