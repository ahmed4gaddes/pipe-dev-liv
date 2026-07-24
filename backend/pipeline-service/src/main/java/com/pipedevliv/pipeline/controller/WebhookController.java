package com.pipedevliv.pipeline.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipedevliv.common.exception.BusinessException;
import com.pipedevliv.pipeline.dto.GitHubWebhookPayload;
import com.pipedevliv.pipeline.service.PipelineService;
import com.pipedevliv.pipeline.service.WebhookSignatureVerifier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pas de @PreAuthorize ici : cet endpoint est intentionnellement accessible sans
 * authentification Spring Security (voir config.SecurityConfig.webhookFilterChain et
 * GatewaySecurityConfig côté api-gateway). L'authenticité de la requête est prouvée par la
 * signature HMAC ci-dessous, indépendamment de toute la chaîne JWT / headers d'identité / X-Internal-Secret
 * habituelle — GitHub ne peut présenter aucun des deux.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookSignatureVerifier signatureVerifier;
    private final PipelineService pipelineService;
    private final ObjectMapper objectMapper;

    @PostMapping("/github")
    public ResponseEntity<Void> handleGitHubWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestBody String rawPayload) {

        if (!signatureVerifier.isValid(rawPayload, signature)) {
            log.warn("Webhook GitHub reçu avec une signature invalide ou absente — rejeté");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!"workflow_run".equals(eventType)) {
            // Hygiène webhook standard : on répond 200 même pour un type d'événement qu'on
            // n'a pas demandé à recevoir (ex: "ping" envoyé par GitHub à la création du webhook).
            return ResponseEntity.ok().build();
        }

        GitHubWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, GitHubWebhookPayload.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Payload webhook GitHub invalide : " + ex.getMessage());
        }

        pipelineService.handleWorkflowRunEvent(payload);

        return ResponseEntity.ok().build();
    }
}
