package com.emakon.reservation.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration TEMPORAIRE : seules la documentation Swagger, la sonde de santé et la
 * consultation des disponibilités sont publiques, tout le reste est refusé (403).
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
                .anyRequest().denyAll());
        return http.build();
    }
}
