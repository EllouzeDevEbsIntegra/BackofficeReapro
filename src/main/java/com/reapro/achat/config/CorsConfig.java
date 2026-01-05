package com.reapro.achat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // IMPORTANT: Utilisez setAllowedOriginPatterns au lieu de setAllowedOrigins
        config.setAllowedOriginPatterns(Arrays.asList("http://localhost:5173"));

        // Autoriser les headers nécessaires
        config.setAllowedHeaders(Arrays.asList("*"));

        // Autoriser les méthodes HTTP (incluez bien OPTIONS pour preflight)
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PATCH","PUT", "DELETE", "OPTIONS", "HEAD"));

        // Autoriser les credentials (cookies, headers d'auth)
        config.setAllowCredentials(true);

        // MaxAge pour éviter les requêtes preflight répétées
        config.setMaxAge(3600L);

        // Appliquer cette config à TOUS les endpoints
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}