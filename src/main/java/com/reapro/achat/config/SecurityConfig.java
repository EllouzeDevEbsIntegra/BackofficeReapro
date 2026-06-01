package com.reapro.achat.config;

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

        // Routes publiques
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
                                "/api/elva-items/sync",
                                "/api/v1/sync-adaptable",
                                "/api/v1/sync-adaptable/sync",
                                "/api/v1/sync-adaptable/sync-status",
                                "/error"
                        ).permitAll()

                        // Toutes les autres routes â†’ protÃ©gÃ©es
                        .anyRequest().authenticated()
        );

        // Ajouter le filtre JWT avant le filtre Spring Security
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

