package com.emakon.reservation.security;

import com.emakon.reservation.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Authentification par jeton opaque (en-tête Authorization: Bearer), et règles d'accès
 * par URL et par rôle.
 *
 * Deux niveaux d'autorisation :
 * - ici, grossier : la route est-elle publique, réservée aux connectés, à un rôle ?
 * - dans les services, fin : ce rendez-vous est-il bien le sien ? (Spring Security ne
 *   connaît pas le propriétaire d'une ressource.)
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthService authService, ObjectMapper mapper)
            throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        // Page d'erreur de Spring Boot : sans cette règle, une erreur 500 serait
                        // masquée par un 401/403 lors du renvoi interne vers /error.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Comme sur Doctolib, on consulte les créneaux sans être connecté.
                        .requestMatchers(HttpMethod.GET, "/api/practitioners/*/availability").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // Seul un patient réserve : l'identité du patient est celle du jeton.
                        .requestMatchers(HttpMethod.POST, "/api/appointments").hasRole("PATIENT")
                        .requestMatchers("/api/**").authenticated()
                        // Toute URL non prévue est refusée : liste blanche plutôt que liste noire.
                        .anyRequest().denyAll())
                .addFilterBefore(new BearerTokenAuthenticationFilter(authService),
                        UsernamePasswordAuthenticationFilter.class)
                // Aucune HttpSession : l'état de connexion est en base (table session),
                // retrouvé à chaque requête grâce au jeton.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF exploite les cookies que le navigateur envoie tout seul. Le jeton,
                // lui, est ajouté explicitement par le frontend dans un en-tête : un autre
                // site ne peut pas le faire à sa place. La protection est donc inutile.
                .csrf(csrf -> csrf.disable())
                // Mêmes réponses Problem Details que le reste de l'API
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) -> {
                            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                            writeProblem(response, mapper, HttpStatus.UNAUTHORIZED, "Authentification requise");
                        })
                        .accessDeniedHandler((request, response, e) ->
                                writeProblem(response, mapper, HttpStatus.FORBIDDEN, "Accès refusé")));
        return http.build();
    }

    /** bcrypt : lent par conception, ce qui rend coûteux les essais en masse de mots de passe. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Ces erreurs surviennent dans la chaîne de filtres, avant les contrôleurs :
    // l'advice global (ApiExceptionHandler) ne les voit pas, on écrit la réponse ici.
    private static void writeProblem(HttpServletResponse response, ObjectMapper mapper, HttpStatus status,
                                     String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
    }
}
