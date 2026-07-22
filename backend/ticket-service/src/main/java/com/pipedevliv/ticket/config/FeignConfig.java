package com.pipedevliv.ticket.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.stream.Collectors;

/**
 * Volontairement PAS annotée @Configuration : elle est passée comme
 * {@code defaultConfiguration} à @EnableFeignClients, mais comme elle vit aussi dans le
 * package scanné (com.pipedevliv.ticket), une annotation @Configuration la ferait
 * enregistrer une deuxième fois dans le contexte principal (bean partagé involontairement
 * au lieu d'être isolé par client Feign) — c'est un piège documenté de Spring Cloud OpenFeign.
 * <p>
 * Les appels Feign vont directement service-à-service via Eureka, sans passer par la
 * Gateway : ce filtre réinjecte donc manuellement les headers que la Gateway aurait mis
 * (X-User-Id, X-User-Roles, X-Internal-Secret), sinon HeaderAuthenticationFilter du
 * service cible rejette l'appel.
 */
public class FeignConfig {

    @Value("${security.internal.gateway-secret}")
    private String gatewaySecret;

    @Bean
    public RequestInterceptor authForwardingInterceptor() {
        return requestTemplate -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                requestTemplate.header("X-User-Id", auth.getName());
                String roles = auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.joining(","));
                requestTemplate.header("X-User-Roles", roles);
            }
            requestTemplate.header("X-Internal-Secret", gatewaySecret);
        };
    }
}
