package com.pipedevliv.apigateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.stream.Collectors;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> {
                    Map<String, Object> claims = jwtAuth.getTokenAttributes();
                    
                    String userId = jwtAuth.getName(); // Habituellement le "sub" (subject ID de Keycloak)
                    String email = (String) claims.get("email");
                    
                    // Extraire les rôles du realm Keycloak
                    String roles = extractRoles(claims);

                    log.debug("User authenticated in Gateway: ID={}, Email={}, Roles={}", userId, email, roles);

                    // Injecter les données dans les headers HTTP pour les microservices
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header("X-User-Id", userId)
                            .header("X-User-Email", email != null ? email : "")
                            .header("X-User-Roles", roles)
                            .build();

                    return exchange.mutate().request(mutatedRequest).build();
                })
                .defaultIfEmpty(exchange) // Si pas de JWT (ex: route non sécurisée), on passe l'exchange tel quel
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
