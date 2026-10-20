package com.emakon.reservation.service;

import com.emakon.reservation.availability.Modality;
import com.emakon.reservation.domain.Appointment;
import com.emakon.reservation.domain.AppointmentStatus;
import java.time.Instant;

/**
 * Rendez-vous tel que le service le renvoie : l'entité ne sort pas de la couche service.
 *
 * @param locationId   null en distanciel
 * @param locationName null en distanciel
 */
public record AppointmentView(
        Long id,
        Instant start,
        Instant end,
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
        Instant cancelledAt) {

    /** À appeler dans la transaction : lit les relations (chargées par l'@EntityGraph). */
    static AppointmentView from(Appointment a) {
        var practitioner = a.getPractitioner();
        var patient = a.getPatient();
        var location = a.getLocation();
        return new AppointmentView(a.getId(), a.getStart(), a.getEnd(), a.getStatus(), a.getModality(),
                a.getConsultationType().getLabel(),
                practitioner.getId(), practitioner.getFirstName() + " " + practitioner.getLastName(),
                patient.getId(), patient.getFirstName() + " " + patient.getLastName(),
                location == null ? null : location.getId(),
                location == null ? null : location.getName(),
                a.getPatientNote(), a.getCancelledAt());
    }
}
