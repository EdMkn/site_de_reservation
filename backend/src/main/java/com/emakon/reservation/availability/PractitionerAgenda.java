package com.emakon.reservation.availability;

import java.util.List;

/**
 * Tout ce qu'on sait de l'agenda d'un praticien, déjà chargé par la couche service.
 *
 * @param rules        son planning récurrent
 * @param absences     ses absences sur la période
 * @param appointments ses rendez-vous qui bloquent l'agenda (statut CONFIRME uniquement :
 *                     le filtrage est fait par le service, ce package ignore les statuts)
 */
public record PractitionerAgenda(List<ScheduleRule> rules, List<TimeRange> absences, List<TimeRange> appointments) {

    public PractitionerAgenda {
        // Copies immuables : l'appelant ne peut plus modifier les listes après coup.
        // List.copyOf refuse aussi les éléments null.
        rules = List.copyOf(rules);
        absences = List.copyOf(absences);
        appointments = List.copyOf(appointments);
    }
}
