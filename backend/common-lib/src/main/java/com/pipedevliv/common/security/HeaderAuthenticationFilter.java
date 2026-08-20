package com.pipedevliv.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Not a {@code @Component}: registered explicitly as a {@code @Bean} by
 * {@link SecurityConfig} so it works regardless of each microservice's base package
 * (see the auto-configuration note on {@link SecurityConfig}).
 */
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HeaderAuthenticationFilter.class);

    private final String expectedGatewaySecret;

    public HeaderAuthenticationFilter(String expectedGatewaySecret) {
        this.expectedGatewaySecret = expectedGatewaySecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        String rolesHeader = request.getHeader("X-User-Roles");
        String providedSecret = request.getHeader("X-Internal-Secret");

        if (userId != null && !userId.isEmpty()) {
            // On ne fait confiance aux headers X-User-* que s'ils sont accompagnés du secret
            // partagé injecté par la Gateway. Sans ça, un appel direct au microservice
            // (contournant la Gateway) pourrait usurper n'importe quelle identité/rôle.
            if (!isValidGatewaySecret(providedSecret)) {
                log.warn("Requête avec X-User-Id mais secret interne manquant/invalide — accès direct refusé (contournement de la Gateway ?)");
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Direct access to this service is not allowed");
                return;
            }

            List<SimpleGrantedAuthority> authorities = List.of();
            if (rolesHeader != null && !rolesHeader.isEmpty()) {
                authorities = Arrays.stream(rolesHeader.split(","))
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isValidGatewaySecret(String providedSecret) {
        if (providedSecret == null || providedSecret.isEmpty()) {
            return false;
        }
        // Comparaison en temps constant pour éviter les attaques par mesure de timing
        byte[] provided = providedSecret.getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedGatewaySecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(provided, expected);
    }
}
