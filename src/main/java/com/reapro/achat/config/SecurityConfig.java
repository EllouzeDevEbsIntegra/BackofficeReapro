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

        // Désactiver CSRF pour les API REST
        http.csrf(csrf -> csrf.disable());

        // Pas de session → 100% JWT
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
                                "/api/auth/change-password"
                        ).permitAll()

                        // Toutes les autres routes → protégées
                        .anyRequest().authenticated()
        );

        // Ajouter le filtre JWT avant le filtre Spring Security
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
