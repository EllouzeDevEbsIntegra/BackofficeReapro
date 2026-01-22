package com.reapro.achat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.client.RestTemplate; // Import added
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {

        // ✅ AJOUT DE .compress(true) ICI
        HttpClient httpClient = HttpClient.create()
                .compress(true)  // <--- C'est cette ligne qui gère le GZIP (Code 31) automatiquement
                .responseTimeout(Duration.ofSeconds(30));

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024)) // 16 MB
                .build();

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
