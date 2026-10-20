package com.emakon.reservation.api;

import com.emakon.reservation.availability.Modality;
import com.emakon.reservation.domain.AppointmentStatus;
import com.emakon.reservation.service.AppointmentView;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Rendez-vous exposé par l'API ; dates dans le fuseau du cabinet, comme SlotResponse. */
public record AppointmentResponse(
        Long id,
        OffsetDateTime start,
        OffsetDateTime end,
        AppointmentStatus status,
        Modality modality,
        String consultationType,
        Long practitionerId,
        String practitionerName,
        Long patientId,
        String patientName,
        Long locationId,
        String locationName,
        String patientNote,
        OffsetDateTime cancelledAt) {

    static AppointmentResponse from(AppointmentView v, ZoneId zone) {
        return new AppointmentResponse(v.id(), v.start().atZone(zone).toOffsetDateTime(),
                v.end().atZone(zone).toOffsetDateTime(), v.status(), v.modality(), v.consultationType(),
                v.practitionerId(), v.practitionerName(), v.patientId(), v.patientName(),
                v.locationId(), v.locationName(), v.patientNote(),
                v.cancelledAt() == null ? null : v.cancelledAt().atZone(zone).toOffsetDateTime());
    }
}
