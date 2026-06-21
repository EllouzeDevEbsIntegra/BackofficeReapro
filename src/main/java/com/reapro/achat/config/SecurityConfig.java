package com.reapro.achat.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // DÃ©sactiver CSRF pour les API REST
        http.csrf(csrf -> csrf.disable());

        // DÃ©sactiver le formulaire de login par dÃ©faut et l'authentification Basic
        http.formLogin(form -> form.disable());
        http.httpBasic(basic -> basic.disable());

        // Pas de session â†’ 100% JWT
        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

        // Routes publiques (authentification réelle uniquement)
        http.authorizeHttpRequests(auth ->
                auth.requestMatchers(
                                "/api/auth/register",
                                "/api/auth/verify-register-code",
                                "/api/auth/login",
                                "/api/auth/refresh-token",
                                "/api/auth/forgot-password",
                                "/api/auth/verify-reset-code",
                                "/api/auth/change-password",
                                "/auth/refresh-token",
                                "/api/version",
                                "/error"
                        ).permitAll()

                        // Endpoints de synchronisation (déclencheurs lourds + lecture catalogue) :
                        // JAMAIS accessibles sans authentification (SEC-001 / SEC-004 / SEC-005).
                        // .authenticated() pour ce premier lot : tous les comptes sont ROLE_ADMIN
                        // aujourd'hui, les rôles ne différencient pas encore les accès — on resserrera
                        // en hasRole("ADMIN") quand un vrai système de rôles sera en place.
                        .requestMatchers(
                                "/api/v1/sync-adaptable",
                                "/api/v1/sync-adaptable/**",
                                "/api/elva-items/sync"
                        ).authenticated()

                        // Toutes les autres routes → protégées
                        .anyRequest().authenticated()
        );

        // ── Sémantique HTTP correcte (CORRECTIF 403/401) ──
        // Sans formLogin/httpBasic, l'entry point par défaut de Spring est Http403ForbiddenEntryPoint :
        // toute requête NON authentifiée (token absent/expiré/invalide → SecurityContext vide) recevait 403.
        //   - NON authentifié           → 401 Unauthorized (le front tente refresh, sinon logout/login).
        //   - authentifié SANS le rôle  → 403 Forbidden    (vrai manque de droits, pas de logout).
        // Pas de détail sensible, aucun token loggé/exposé.
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"FORBIDDEN\"}");
                })
        );

        // Ajouter le filtre JWT avant le filtre Spring Security
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

