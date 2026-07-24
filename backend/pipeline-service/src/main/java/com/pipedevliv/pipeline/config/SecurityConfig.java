package com.pipedevliv.pipeline.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Carve-out local pour le webhook GitHub, en plus de la chaîne de sécurité par défaut de
 * common-lib (SecurityConfig, {@code .anyRequest().authenticated()}). GitHub ne peut
 * présenter ni JWT Keycloak ni les headers d'identité (X-User-Id, etc.) / X-Internal-Secret injectés par la
 * Gateway : ce endpoint prouve son authenticité lui-même via la signature HMAC
 * X-Hub-Signature-256 (voir WebhookSignatureVerifier), pas via la chaîne d'auth habituelle.
 * <p>
 * {@code @Order(1)} lui donne priorité sur la chaîne de common-lib (non ordonnée, donc
 * évaluée en dernier) uniquement pour les requêtes qui matchent son {@code securityMatcher} —
 * toutes les autres routes de ce service restent protégées normalement.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain webhookFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/webhooks/github")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
