package com.pipedevliv.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Registered as an {@code @AutoConfiguration} (not a plain {@code @Configuration}) and
 * listed in {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * <p>
 * A microservice's default {@code @ComponentScan} (implied by {@code @SpringBootApplication})
 * only scans its own base package and below — e.g. {@code com.pipedevliv.ticket} — which never
 * reaches the sibling package {@code com.pipedevliv.common}. Relying on component scanning here
 * would silently leave every future microservice with no security filter active at all. Spring
 * Boot's auto-configuration mechanism activates regardless of the consuming service's package,
 * as long as common-lib is on its classpath.
 * <p>
 * All beans below are wired explicitly via {@code @Bean} methods rather than {@code @Component}
 * scanning, for the same reason.
 */
@AutoConfiguration
@EnableWebSecurity
@EnableMethodSecurity // Permet d'utiliser @PreAuthorize dans les contrôleurs
public class SecurityConfig {

    @Bean
    public HeaderAuthenticationFilter headerAuthenticationFilter(
            @Value("${security.internal.gateway-secret}") String gatewaySecret) {
        return new HeaderAuthenticationFilter(gatewaySecret);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, HeaderAuthenticationFilter headerAuthenticationFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Healthcheck Docker / pipeline de déploiement (Phase 9)
                .requestMatchers("/actuator/health/**").permitAll()
                .anyRequest().authenticated()
            )
            // On ajoute notre filtre personnalisé avant le filtre standard de Spring Security
            .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
