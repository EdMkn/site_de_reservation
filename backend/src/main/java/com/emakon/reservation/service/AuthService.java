package com.emakon.reservation.service;

import com.emakon.reservation.domain.Session;
import com.emakon.reservation.domain.UserAccount;
import com.emakon.reservation.repository.SessionRepository;
import com.emakon.reservation.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connexion, déconnexion, et résolution d'un jeton en utilisateur.
 *
 * Jeton opaque : 32 octets aléatoires, sans aucune signification. Seul son hash est en
 * base ; le vérifier coûte une requête par appel, en échange d'une révocation immédiate
 * (déconnexion = suppression de la ligne), ce qu'un JWT ne permet pas.
 */
@Service
public class AuthService {

    private static final int TOKEN_BYTES = 32;

    private final UserAccountRepository users;
    private final SessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final SessionProperties properties;
    private final Clock clock;
    // SecureRandom et non Random : ses valeurs sont imprévisibles, même en connaissant
    // les précédentes. Thread-safe, une instance suffit.
    private final SecureRandom random = new SecureRandom();
    // Hash bcrypt d'un mot de passe quelconque, comparé quand l'email est inconnu : la
    // réponse prend alors autant de temps que pour un vrai compte, et la durée ne trahit
    // pas l'existence de l'email.
    private final String dummyHash;

    public AuthService(UserAccountRepository users, SessionRepository sessions, PasswordEncoder passwordEncoder,
                       SessionProperties properties, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password");
    }

    @Transactional
    public LoginResult login(String email, String password) {
        Optional<UserAccount> account = users.findByEmailIgnoreCase(email);
        // Comparaison toujours faite, même sans compte (voir dummyHash)
        boolean passwordMatches = passwordEncoder.matches(password,
                account.map(UserAccount::getPasswordHash).orElse(dummyHash));
        if (account.isEmpty() || !passwordMatches || !account.get().isActive()) {
            throw new InvalidCredentialsException();
        }

        String token = newToken();
        Session session = sessions.save(new Session(account.get(), hash(token), clock.instant(),
                properties.idleTimeout(), properties.maxLifetime()));
        return new LoginResult(token, session.getExpiresAt(), toAuthenticatedUser(session));
    }

    /**
     * Appelée par le filtre à chaque requête portant un jeton. Une session valide voit son
     * expiration repoussée (UPDATE au commit, par dirty checking) : c'est l'expiration
     * glissante. Jeton inconnu, expiré ou compte désactivé : Optional vide.
     */
    @Transactional
    public Optional<AuthenticatedUser> authenticate(String token) {
        Instant now = clock.instant();
        return sessions.findByTokenHashWithUser(hash(token))
                .filter(session -> session.isValidAt(now, properties.maxLifetime()))
                .filter(session -> session.getUser().isActive())
                .map(session -> {
                    session.extend(now, properties.idleTimeout(), properties.maxLifetime());
                    return toAuthenticatedUser(session);
                });
    }

    /** Suppression physique : une session terminée n'a aucune valeur historique. */
    @Transactional
    public void logout(Long sessionId) {
        sessions.deleteById(sessionId);
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        // Base64 "URL-safe" sans '=' final : transmissible tel quel dans un en-tête HTTP
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 suffit, bcrypt serait inutilement lent : bcrypt protège les mots de passe
     * humains, devinables, contre les essais en masse ; un jeton de 256 bits aléatoires
     * est impossible à deviner, même avec un hash rapide.
     */
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 est garanti par toute JVM", e);
        }
    }

    private static AuthenticatedUser toAuthenticatedUser(Session session) {
        UserAccount user = session.getUser();
        return new AuthenticatedUser(user.getId(), session.getId(), user.getEmail(), user.getRole());
    }
}
