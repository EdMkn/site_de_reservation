package com.emakon.reservation.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration TEMPORAIRE : seules la documentation Swagger, la sonde de santé, la
 * consultation des disponibilités et la réservation sont publiques, tout le reste est
 * refusé (403).
 * À remplacer par les vraies règles d'authentification et d'autorisation.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // Comme sur Doctolib, on consulte les créneaux sans être connecté ;
                // il faudra l'être pour réserver.
                .requestMatchers(HttpMethod.GET, "/api/practitioners/*/availability").permitAll()
                // PROVISOIRE (étape 6) : réservation ouverte, le patient est désigné dans le corps.
                .requestMatchers(HttpMethod.POST, "/api/appointments").permitAll()
                .anyRequest().denyAll())
            // PROVISOIRE : sans session ni cookie d'authentification, pas de risque CSRF.
            // À réexaminer à l'étape 6 selon la façon dont le jeton est transmis.
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
