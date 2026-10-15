package com.emakon.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Exception au planning : congés, formation, fermeture (table absence). */
@Entity
@Table(name = "absence")
public class Absence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "praticien_id", nullable = false)
    private Practitioner practitioner;

    // TIMESTAMPTZ -> Instant : un point précis sur la ligne du temps, sans fuseau à
    // interpréter. C'est aussi le type qu'attend le package availability (TimeRange).
    @Column(name = "debut", nullable = false)
    private Instant start;

    @Column(name = "fin", nullable = false)
    private Instant end;

    /** Usage interne, jamais exposé au patient. */
    @Column(name = "raison")
    private String reason;

    protected Absence() {
    }

    public Absence(Practitioner practitioner, Instant start, Instant end, String reason) {
        this.practitioner = practitioner;
        this.start = start;
        this.end = end;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Practitioner getPractitioner() {
        return practitioner;
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }

    public String getReason() {
        return reason;
    }
}
