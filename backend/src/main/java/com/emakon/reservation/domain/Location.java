package com.emakon.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Cabinet ou centre de santé (table lieu), potentiellement partagé entre praticiens. */
@Entity
@Table(name = "lieu")
public class Location {

    // BIGSERIAL : la base génère l'id à l'insertion. IDENTITY dit à Hibernate de le relire
    // après l'INSERT. La stratégie par défaut (SEQUENCE, allocationSize = 50) attendrait une
    // séquence incrémentée de 50 : incompatible avec le schéma.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom", nullable = false, length = 150)
    private String name;

    @Column(name = "adresse", nullable = false)
    private String address;

    // CHAR(5) en base : sans @JdbcTypeCode, Hibernate attendrait un varchar et la
    // validation du schéma échouerait.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "code_postal", nullable = false, length = 5)
    private String postalCode;

    @Column(name = "ville", nullable = false, length = 100)
    private String city;

    /** Réservé à JPA, qui instancie l'entité avant de remplir ses champs. */
    protected Location() {
    }

    public Location(String name, String address, String postalCode, String city) {
        this.name = name;
        this.address = address;
        this.postalCode = postalCode;
        this.city = city;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCity() {
        return city;
    }
}
