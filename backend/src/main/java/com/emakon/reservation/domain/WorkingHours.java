package com.emakon.reservation.domain;

import com.emakon.reservation.availability.Modality;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Plage horaire récurrente du planning (table plage_horaire), en heure locale du cabinet.
 * Le service la convertira en ScheduleRule pour le calcul des disponibilités.
 */
@Entity
@Table(name = "plage_horaire")
public class WorkingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "praticien_id", nullable = false)
    private Practitioner practitioner;

    @Convert(converter = DayOfWeekConverter.class)
    @Column(name = "jour_semaine", nullable = false)
    private DayOfWeek day;

    @Column(name = "heure_debut", nullable = false)
    private LocalTime startTime;

    @Column(name = "heure_fin", nullable = false)
    private LocalTime endTime;

    // Enum partagée avec le package availability (Java pur, aucune annotation JPA dedans).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "modalite", nullable = false, columnDefinition = "modalite")
    private Modality modality;

    // Null en distanciel : la cohérence modalité / lieu est garantie par le CHECK SQL.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieu_id")
    private Location location;

    @Column(name = "valide_du", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valide_au")
    private LocalDate validUntil;

    protected WorkingHours() {
    }

    public WorkingHours(Practitioner practitioner, DayOfWeek day, LocalTime startTime, LocalTime endTime,
                        Modality modality, Location location, LocalDate validFrom, LocalDate validUntil) {
        this.practitioner = practitioner;
        this.day = day;
        this.startTime = startTime;
        this.endTime = endTime;
        this.modality = modality;
        this.location = location;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    public Long getId() {
        return id;
    }

    public Practitioner getPractitioner() {
        return practitioner;
    }

    public DayOfWeek getDay() {
        return day;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Modality getModality() {
        return modality;
    }

    public Location getLocation() {
        return location;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }
}
