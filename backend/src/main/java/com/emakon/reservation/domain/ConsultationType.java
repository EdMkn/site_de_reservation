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
import java.time.Duration;

/** Motif de consultation propre à un praticien (table motif). Sa durée fixe la taille des créneaux. */
@Entity
@Table(name = "motif")
public class ConsultationType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @ManyToOne est EAGER par défaut en JPA : charger 50 motifs déclencherait jusqu'à
    // 50 requêtes supplémentaires pour leurs praticiens (problème N+1). On passe en LAZY
    // partout, et le service chargera explicitement (JOIN FETCH) ce dont il a besoin.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "praticien_id", nullable = false)
    private Practitioner practitioner;

    @Column(name = "libelle", nullable = false, length = 150)
    private String label;

    // SMALLINT en base -> short en Java (un int échouerait à la validation du schéma).
    @Column(name = "duree_minutes", nullable = false)
    private short durationMinutes;

    @Column(name = "distanciel_possible", nullable = false)
    private boolean remoteAllowed;

    @Column(name = "actif", nullable = false)
    private boolean active = true;

    protected ConsultationType() {
    }

    public ConsultationType(Practitioner practitioner, String label, Duration duration, boolean remoteAllowed) {
        this.practitioner = practitioner;
        this.label = label;
        this.durationMinutes = (short) duration.toMinutes();
        this.remoteAllowed = remoteAllowed;
    }

    public Long getId() {
        return id;
    }

    public Practitioner getPractitioner() {
        return practitioner;
    }

    public String getLabel() {
        return label;
    }

    /** Exposée en Duration : le reste du code ne manipule pas de "minutes" brutes. */
    public Duration getDuration() {
        return Duration.ofMinutes(durationMinutes);
    }

    public boolean isRemoteAllowed() {
        return remoteAllowed;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }
}
