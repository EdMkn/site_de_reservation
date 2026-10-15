package com.emakon.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Spécialisation praticien (table praticien). Même mécanisme d'héritage que Patient. */
@Entity
@Table(name = "praticien")
@PrimaryKeyJoinColumn(name = "utilisateur_id")
public class Practitioner extends UserAccount {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "numero_rpps", nullable = false, unique = true, length = 11)
    private String rppsNumber;

    @Column(name = "presentation", columnDefinition = "text")
    private String presentation;

    // N–N via la table de liaison. Set et non List : pas de doublon possible (comme la
    // clé primaire composée), et Hibernate gère mieux les Set pour les ManyToMany.
    // LAZY (défaut des collections) : les spécialités ne sont chargées que si on y accède.
    // Relation unidirectionnelle : Specialty n'a pas besoin de connaître ses praticiens ;
    // la recherche "praticiens d'une spécialité" passera par une requête du repository.
    @ManyToMany
    @JoinTable(
            name = "praticien_specialite",
            joinColumns = @JoinColumn(name = "praticien_id"),
            inverseJoinColumns = @JoinColumn(name = "specialite_id"))
    private Set<Specialty> specialties = new HashSet<>();

    protected Practitioner() {
    }

    public Practitioner(String email, String passwordHash, String lastName, String firstName, String rppsNumber) {
        super(Role.PRATICIEN, email, passwordHash, lastName, firstName);
        this.rppsNumber = rppsNumber;
    }

    public String getRppsNumber() {
        return rppsNumber;
    }

    public String getPresentation() {
        return presentation;
    }

    public void setPresentation(String presentation) {
        this.presentation = presentation;
    }

    /** Vue en lecture seule : on modifie la collection par les méthodes ci-dessous. */
    public Set<Specialty> getSpecialties() {
        return Collections.unmodifiableSet(specialties);
    }

    public void addSpecialty(Specialty specialty) {
        specialties.add(specialty);
    }

    public void removeSpecialty(Specialty specialty) {
        specialties.remove(specialty);
    }
}
