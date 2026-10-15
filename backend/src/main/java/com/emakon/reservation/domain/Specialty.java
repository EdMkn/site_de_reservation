package com.emakon.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Référentiel des spécialités (table specialite). */
@Entity
@Table(name = "specialite")
public class Specialty {

    // SERIAL (int) en base, d'où Integer et non Long.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "libelle", nullable = false, unique = true, length = 100)
    private String label;

    protected Specialty() {
    }

    public Specialty(String label) {
        this.label = label;
    }

    public Integer getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }
}
