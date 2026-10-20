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
import java.time.Instant;

/**
 * Session de connexion (table session) : un jeton opaque, dont seul le hash SHA-256 est
 * stocké. Une fuite de la table ne donne donc aucun jeton utilisable.
 *
 * Deux expirations :
 * - glissante (expire_le) : repoussée à chaque requête, la session meurt après une
 *   période d'inactivité ;
 * - absolue (cree_le + durée maximale) : calculée, pas stockée ; même utilisée tous les
 *   jours, une session finit par expirer.
 */
@Entity
@Table(name = "session")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY comme les autres relations ; la requête d'authentification charge
    // l'utilisateur explicitement (join fetch), car elle en a toujours besoin.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false, updatable = false)
    private UserAccount user;

    @Column(name = "token_hash", nullable = false, unique = true, updatable = false)
    private String tokenHash;

    @Column(name = "expire_le", nullable = false)
    private Instant expiresAt;

    // Contrairement aux autres cree_le, fixé par l'application et non par le DEFAULT now()
    // de la base : il entre dans le calcul de l'expiration absolue, qui doit utiliser la
    // même horloge (Clock) que le reste du code, figée dans les tests.
    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant createdAt;

    protected Session() {
    }

    public Session(UserAccount user, String tokenHash, Instant now, Duration idleTimeout, Duration maxLifetime) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = nextExpiry(now, idleTimeout, maxLifetime);
    }

    /** Session utilisable à l'instant donné : ni inactive trop longtemps, ni trop ancienne. */
    public boolean isValidAt(Instant now, Duration maxLifetime) {
        return now.isBefore(expiresAt) && now.isBefore(createdAt.plus(maxLifetime));
    }

    /** Expiration glissante : repoussée à partir de maintenant, sans dépasser la limite absolue. */
    public void extend(Instant now, Duration idleTimeout, Duration maxLifetime) {
        this.expiresAt = nextExpiry(now, idleTimeout, maxLifetime);
    }

    private Instant nextExpiry(Instant now, Duration idleTimeout, Duration maxLifetime) {
        Instant idle = now.plus(idleTimeout);
        Instant absolute = createdAt.plus(maxLifetime);
        return idle.isBefore(absolute) ? idle : absolute;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
