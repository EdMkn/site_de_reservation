package com.emakon.reservation.service;

import com.emakon.reservation.domain.ConsultationType;
import com.emakon.reservation.domain.Location;
import com.emakon.reservation.domain.Specialty;
import java.util.List;

/**
 * Vues de lecture de l'annuaire des praticiens. Ce sont déjà des DTO (records sans
 * entité) et aucune conversion n'est nécessaire (pas de dates) : l'API les expose
 * telles quelles.
 */
public final class PractitionerViews {

    private PractitionerViews() {
    }

    public record SpecialtyView(Integer id, String label) {
        static SpecialtyView from(Specialty s) {
            return new SpecialtyView(s.getId(), s.getLabel());
        }
    }

    public record LocationView(Long id, String name, String address, String postalCode, String city) {
        static LocationView from(Location l) {
            return new LocationView(l.getId(), l.getName(), l.getAddress(), l.getPostalCode(), l.getCity());
        }
    }

    public record ConsultationTypeView(Long id, String label, long durationMinutes, boolean remoteAllowed) {
        static ConsultationTypeView from(ConsultationType t) {
            return new ConsultationTypeView(t.getId(), t.getLabel(), t.getDuration().toMinutes(), t.isRemoteAllowed());
        }
    }

    /** Ligne de l'annuaire. */
    public record PractitionerSummary(Long id, String firstName, String lastName,
                                      List<SpecialtyView> specialties, List<String> cities) {
    }

    /** Fiche d'un praticien : ce qu'il faut pour choisir un motif puis un créneau. */
    public record PractitionerDetail(Long id, String firstName, String lastName, String presentation,
                                     List<SpecialtyView> specialties,
                                     List<ConsultationTypeView> consultationTypes,
                                     List<LocationView> locations) {
    }
}
