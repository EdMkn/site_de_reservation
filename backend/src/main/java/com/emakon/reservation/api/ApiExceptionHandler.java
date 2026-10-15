package com.emakon.reservation.api;

import com.emakon.reservation.service.InvalidPeriodException;
import com.emakon.reservation.service.NotFoundException;
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
}
