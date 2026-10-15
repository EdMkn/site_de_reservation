package com.emakon.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Spécialisation patient (table patient). Sa clé primaire utilisateur_id est aussi la
 * clé étrangère vers utilisateur.
 *
 * La colonne patient.role n'est pas mappée : son DEFAULT 'PATIENT' la remplit, et elle
 * ne sert qu'à la clé étrangère composée qui vérifie le rôle côté base.
 */
@Entity
@Table(name = "patient")
@PrimaryKeyJoinColumn(name = "utilisateur_id")
public class Patient extends UserAccount {

    @Column(name = "date_naissance", nullable = false)
    private LocalDate birthDate;

    protected Patient() {
    }

    public Patient(String email, String passwordHash, String lastName, String firstName, LocalDate birthDate) {
        super(Role.PATIENT, email, passwordHash, lastName, firstName);
        this.birthDate = birthDate;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }
}
