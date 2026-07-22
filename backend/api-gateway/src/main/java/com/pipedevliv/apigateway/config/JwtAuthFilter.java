package com.pipedevliv.apigateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    // Headers qu'un client ne doit jamais pouvoir définir lui-même : toujours retirés de la
    // requête entrante avant que la Gateway ne réinjecte ses propres valeurs de confiance.
    private static final List<String> TRUSTED_HEADERS = List.of(
            "X-User-Id", "X-User-Email", "X-User-Name", "X-User-Roles", "X-Internal-Secret");

    @Value("${security.internal.gateway-secret}")
    private String gatewaySecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest strippedRequest = exchange.getRequest().mutate()
                .headers(headers -> TRUSTED_HEADERS.forEach(headers::remove))
                .build();
        ServerWebExchange strippedExchange = exchange.mutate().request(strippedRequest).build();

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> {
                    Map<String, Object> claims = jwtAuth.getTokenAttributes();

                    String userId = jwtAuth.getName(); // Habituellement le "sub" (subject ID de Keycloak)
                    String email = (String) claims.get("email");
                    String name = (String) claims.get("name");

                    // Extraire les rôles du realm Keycloak (déjà préfixés ROLE_ côté Keycloak)
                    String roles = extractRoles(claims);

                    log.debug("User authenticated in Gateway: ID={}, Email={}, Roles={}", userId, email, roles);

                    // Injecter les headers de confiance pour les microservices, accompagnés du secret
                    // partagé qui prouve que la requête provient bien de la Gateway.
                    ServerHttpRequest mutatedRequest = strippedExchange.getRequest().mutate()
                            .header("X-User-Id", userId)
                            .header("X-User-Email", email != null ? email : "")
                            .header("X-User-Name", name != null ? name : "")
                            .header("X-User-Roles", roles)
                            .header("X-Internal-Secret", gatewaySecret)
                            .build();

                    return strippedExchange.mutate().request(mutatedRequest).build();
                })
                // Si pas de JWT (ex: route publique), on passe l'exchange nettoyé : les éventuels
                // headers d'identité usurpés par le client ont déjà été retirés ci-dessus.
                .defaultIfEmpty(strippedExchange)
                .flatMap(chain::filter);
    }

    @SuppressWarnings("unchecked")
    private String extractRoles(Map<String, Object> claims) {
        if (claims.containsKey("realm_access")) {
            Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");
            if (realmAccess.containsKey("roles")) {
                List<String> rolesList = (List<String>) realmAccess.get("roles");
                return String.join(",", rolesList);
            }
        }
        return "";
    }

    @Override
    public int getOrder() {
        return -1; // S'exécute tôt dans la chaîne de filtres
    }
}
