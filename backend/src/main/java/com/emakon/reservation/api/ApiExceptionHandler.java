package com.emakon.reservation.api;

import com.emakon.reservation.domain.CancellationNotAllowedException;
import com.emakon.reservation.service.AccessForbiddenException;
import com.emakon.reservation.service.InvalidCredentialsException;
import com.emakon.reservation.service.InvalidPeriodException;
import com.emakon.reservation.service.NotFoundException;
import com.emakon.reservation.service.PatientUnavailableException;
import com.emakon.reservation.service.SlotUnavailableException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduction centralisée des erreurs en réponses HTTP, au format standard
 * "Problem Details" (RFC 9457) : {"type", "title", "status", "detail", "instance"}.
 *
 * Hériter de ResponseEntityExceptionHandler fait aussi passer au même format les erreurs
 * levées par Spring MVC lui-même (paramètre manquant, date mal formée -> 400).
 *
 * Les services ne connaissent pas HTTP : ils lèvent des exceptions métier, et c'est ici
 * seulement qu'on décide du code de statut.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidPeriodException.class)
    ProblemDetail handleInvalidPeriod(InvalidPeriodException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(AccessForbiddenException.class)
    ProblemDetail handleForbidden(AccessForbiddenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(CancellationNotAllowedException.class)
    ProblemDetail handleCancellationNotAllowed(CancellationNotAllowedException e) {
        return conflict("/problems/cancellation-not-allowed", "Annulation impossible", e.getMessage());
    }

    // Deux conflits de réservation, même statut 409 : le champ "type" (stable, à tester côté frontend)
    // les distingue ; "detail" est destiné à l'humain et peut changer.

    @ExceptionHandler(SlotUnavailableException.class)
    ProblemDetail handleSlotUnavailable(SlotUnavailableException e) {
        return conflict("/problems/slot-unavailable", "Créneau indisponible", e.getMessage());
    }

    @ExceptionHandler(PatientUnavailableException.class)
    ProblemDetail handlePatientUnavailable(PatientUnavailableException e) {
        return conflict("/problems/patient-unavailable", "Patient déjà occupé", e.getMessage());
    }

    private static ProblemDetail conflict(String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        return problem;
    }
}
