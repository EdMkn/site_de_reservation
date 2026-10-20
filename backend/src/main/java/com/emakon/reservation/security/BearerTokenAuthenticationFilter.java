package com.emakon.reservation.security;

import com.emakon.reservation.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lit "Authorization: Bearer <jeton>", et si le jeton correspond à une session valide,
 * déclare la requête authentifiée en plaçant l'utilisateur dans le SecurityContext.
 *
 * Ce filtre ne refuse rien lui-même : sans jeton (ou avec un jeton invalide), la requête
 * continue anonyme, et ce sont les règles de SecurityConfig qui décident (401 si la route
 * exige d'être connecté, accès normal si elle est publique).
 *
 * Pas de @Component : Spring Boot enregistrerait alors le filtre une seconde fois, comme
 * filtre de servlet, en dehors de la chaîne Spring Security. Il est créé dans SecurityConfig.
 */
class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final AuthService authService;

    BearerTokenAuthenticationFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIX)) {
            authService.authenticate(header.substring(PREFIX.length())).ifPresent(user -> {
                // Préfixe ROLE_ : convention de Spring Security, attendue par hasRole("PATIENT")
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
                var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
