package com.pipedevliv.apigateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(authentication -> {
                    if (authentication instanceof JwtAuthenticationToken) {
                        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
                        
                        String userId = jwt.getSubject();
                        String email = jwt.getClaimAsString("email");
                        String roles = extractRoles(jwt);

                        ServerHttpRequest request = exchange.getRequest().mutate()
                                .header("X-User-Id", userId != null ? userId : "")
                                .header("X-User-Email", email != null ? email : "")
                                .header("X-User-Roles", roles)
                                .build();

                        return chain.filter(exchange.mutate().request(request).build());
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private String extractRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof Collection) {
                return ((Collection<?>) rolesObj).stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(","));
            }
        }
        return "";
    }

    @Override
    public int getOrder() {
        return -1; // Execute early in the filter chain
    }
}
