package com.pipedevliv.pipeline.controller;

import com.pipedevliv.common.exception.GlobalExceptionHandler;
import com.pipedevliv.common.security.SecurityConfig;
import com.pipedevliv.pipeline.dto.GitHubWebhookPayload;
import com.pipedevliv.pipeline.service.PipelineService;
import com.pipedevliv.pipeline.service.WebhookSignatureVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrairement à tous les autres tests de contrôleur du projet, {@code addFilters} n'est
 * PAS mis à false ici : c'est justement la chaîne de filtres réelle (voir
 * config.SecurityConfig.webhookFilterChain, @Order(1)) qu'on veut prouver — qu'une requête
 * sans JWT ni X-User-Id atteint bien le contrôleur (carve-out permitAll), et que c'est la
 * vérification manuelle de signature dans le contrôleur, pas Spring Security, qui rejette
 * une requête non authentique.
 */
@WebMvcTest(WebhookController.class)
@Import({SecurityConfig.class, com.pipedevliv.pipeline.config.SecurityConfig.class, GlobalExceptionHandler.class, WebhookSignatureVerifier.class})
@AutoConfigureMockMvc(addFilters = true)
class WebhookControllerTest {

    private static final String SECRET = "test-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PipelineService pipelineService;

    @Test
    void noAuthenticationAtAll_validSignature_reachesController() throws Exception {
        String body = """
                {"action":"in_progress","workflow_run":{"id":999,"status":"in_progress"}}
                """;

        mockMvc.perform(post("/api/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", "sha256=" + hmac(body))
                        .content(body))
                .andExpect(status().isOk());

        verify(pipelineService).handleWorkflowRunEvent(any(GitHubWebhookPayload.class));
    }

    @Test
    void invalidSignature_rejected() throws Exception {
        String body = """
                {"action":"in_progress","workflow_run":{"id":999,"status":"in_progress"}}
                """;

        mockMvc.perform(post("/api/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", "sha256=deadbeef")
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(pipelineService, never()).handleWorkflowRunEvent(any());
    }

    @Test
    void missingSignature_rejected() throws Exception {
        String body = "{}";

        mockMvc.perform(post("/api/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "workflow_run")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonWorkflowRunEvent_ignoredWithOk() throws Exception {
        String body = "{\"zen\":\"Keep it logically awesome.\"}";

        mockMvc.perform(post("/api/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "ping")
                        .header("X-Hub-Signature-256", "sha256=" + hmac(body))
                        .content(body))
                .andExpect(status().isOk());

        verify(pipelineService, never()).handleWorkflowRunEvent(any());
    }

    private String hmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
