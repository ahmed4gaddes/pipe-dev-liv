package com.pipedevliv.pipeline.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Volontairement PAS annotée @Configuration : passée comme {@code defaultConfiguration} à
 * @EnableFeignClients, mais elle vit aussi dans le package scanné (com.pipedevliv.pipeline) —
 * l'annoter la ferait enregistrer une deuxième fois dans le contexte principal (piège
 * documenté de Spring Cloud OpenFeign, même raisonnement que ticket-service.FeignConfig).
 * <p>
 * Contrairement à ticket-service, le seul appelant Feign sortant de ce service est le chemin
 * webhook (déclenché par GitHub, sans utilisateur authentifié) — il n'y a donc jamais
 * d'{@code Authentication} réelle à transmettre. On affirme une identité système fixe à la
 * place, reconnue côté ticket-service par le rôle ROLE_SYSTEM (voir TicketController).
 */
public class FeignConfig {

    @Value("${security.internal.gateway-secret}")
    private String gatewaySecret;

    @Bean
    public RequestInterceptor systemIdentityInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("X-User-Id", "pipeline-service");
            requestTemplate.header("X-User-Roles", "ROLE_SYSTEM");
            requestTemplate.header("X-Internal-Secret", gatewaySecret);
        };
    }
}
