package com.emakon.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Socle commun aux patients et aux praticiens (table utilisateur).
 *
 * Héritage JOINED : une table par classe, reliées par la clé primaire. Charger un Patient
 * fait une jointure utilisateur + patient. Abstraite : un utilisateur est toujours soit
 * patient, soit praticien, jamais "juste" un utilisateur.
 *
 * Nommée UserAccount plutôt que User pour éviter la confusion avec la classe User de
 * Spring Security, qu'on utilisera pour l'authentification.
 *
 * equals/hashCode ne sont pas redéfinis : l'identité Java suffit, car Hibernate garantit
 * une seule instance par ligne dans une même session. Les redéfinir sur l'id serait faux
 * avant l'insertion (id null) et casserait les Set qui contiennent l'entité.
 */
@Entity
@Table(name = "utilisateur")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "mot_de_passe_hash", nullable = false)
    private String passwordHash;

    // Champ normal, fixé par le constructeur de la sous-classe, et non discriminateur JPA :
    // avec JOINED, Hibernate n'a pas besoin de discriminateur (il déduit le type par jointure),
    // et un discriminateur serait envoyé en varchar, refusé par la colonne de type enum.
    // updatable = false : le schéma interdit de changer de rôle (clé étrangère composée).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, updatable = false, columnDefinition = "role_utilisateur")
    private Role role;

    @Column(name = "nom", nullable = false, length = 100)
    private String lastName;

    @Column(name = "prenom", nullable = false, length = 100)
    private String firstName;

    @Column(name = "telephone", length = 20)
    private String phone;

    @Column(name = "actif", nullable = false)
    private boolean active = true;

    // Rempli par le DEFAULT now() de la base. insertable = false : Hibernate n'envoie pas
    // la colonne ; @Generated : il relit la valeur produite par la base après l'INSERT.
    @Generated
    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    protected UserAccount(Role role, String email, String passwordHash, String lastName, String firstName) {
        this.role = role;
        this.email = email;
        this.passwordHash = passwordHash;
        this.lastName = lastName;
        this.firstName = firstName;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
