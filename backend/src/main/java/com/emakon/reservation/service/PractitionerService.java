package com.emakon.reservation.service;

import com.emakon.reservation.domain.Practitioner;
import com.emakon.reservation.repository.ConsultationTypeRepository;
import com.emakon.reservation.repository.PractitionerLocation;
import com.emakon.reservation.repository.PractitionerRepository;
import com.emakon.reservation.repository.SpecialtyRepository;
import com.emakon.reservation.repository.WorkingHoursRepository;
import com.emakon.reservation.service.PractitionerViews.ConsultationTypeView;
import com.emakon.reservation.service.PractitionerViews.LocationView;
import com.emakon.reservation.service.PractitionerViews.PractitionerDetail;
import com.emakon.reservation.service.PractitionerViews.PractitionerSummary;
import com.emakon.reservation.service.PractitionerViews.SpecialtyView;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Annuaire public : liste des praticiens, et fiche d'un praticien. */
@Service
public class PractitionerService {

    private final PractitionerRepository practitioners;
    private final SpecialtyRepository specialties;
    private final ConsultationTypeRepository consultationTypes;
    private final WorkingHoursRepository workingHours;
    private final Clock clock;
    private final ZoneId zone;

    public PractitionerService(PractitionerRepository practitioners, SpecialtyRepository specialties,
                               ConsultationTypeRepository consultationTypes, WorkingHoursRepository workingHours,
                               Clock clock, ZoneId practiceZone) {
        this.practitioners = practitioners;
        this.specialties = specialties;
        this.consultationTypes = consultationTypes;
        this.workingHours = workingHours;
        this.clock = clock;
        this.zone = practiceZone;
    }

    @Transactional(readOnly = true)
    public List<SpecialtyView> listSpecialties() {
        return specialties.findAllByOrderByLabelAsc().stream().map(SpecialtyView::from).toList();
    }

    /**
     * Deux requêtes en tout, quel que soit le nombre de praticiens : une pour les praticiens
     * et leurs spécialités, une pour tous les lieux.
     *
     * Le filtre par spécialité est appliqué en Java : l'annuaire est petit. Avec des
     * milliers de praticiens, il faudrait filtrer et paginer en SQL.
     *
     * @param specialtyId null : tous les praticiens
     */
    @Transactional(readOnly = true)
    public List<PractitionerSummary> list(Integer specialtyId) {
        Map<Long, List<String>> citiesByPractitioner = workingHours.findCurrentLocations(today()).stream()
                .collect(Collectors.groupingBy(PractitionerLocation::practitionerId,
                        Collectors.mapping(pl -> pl.location().getCity(),
                                Collectors.collectingAndThen(Collectors.toCollection(TreeSet::new),
                                        List::copyOf))));

        return practitioners.findByActiveTrueOrderByLastNameAscFirstNameAsc().stream()
                .filter(p -> specialtyId == null
                        || p.getSpecialties().stream().anyMatch(s -> s.getId().equals(specialtyId)))
                .map(p -> new PractitionerSummary(p.getId(), p.getFirstName(), p.getLastName(),
                        specialtiesOf(p), citiesByPractitioner.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PractitionerDetail get(Long practitionerId) {
        Practitioner p = practitioners.findById(practitionerId)
                .filter(Practitioner::isActive)
                .orElseThrow(() -> new NotFoundException("Praticien introuvable : " + practitionerId));
        return new PractitionerDetail(p.getId(), p.getFirstName(), p.getLastName(), p.getPresentation(),
                specialtiesOf(p),
                consultationTypes.findByPractitionerIdAndActiveTrueOrderByLabelAsc(practitionerId).stream()
                        .map(ConsultationTypeView::from).toList(),
                workingHours.findCurrentLocationsOf(practitionerId, today()).stream()
                        .map(LocationView::from)
                        .sorted(Comparator.comparing(LocationView::name))
                        .toList());
    }

    private static List<SpecialtyView> specialtiesOf(Practitioner p) {
        return p.getSpecialties().stream()
                .map(SpecialtyView::from)
                .sorted(Comparator.comparing(SpecialtyView::label))
                .toList();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }
}
