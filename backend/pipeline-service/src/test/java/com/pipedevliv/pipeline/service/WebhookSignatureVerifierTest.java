package com.pipedevliv.pipeline.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private static final String SECRET = "test-webhook-secret";
    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(SECRET);

    @Test
    void isValid_correctSignature_true() throws Exception {
        String body = "{\"action\":\"completed\"}";
        String signature = "sha256=" + hmac(body, SECRET);

        assertThat(verifier.isValid(body, signature)).isTrue();
    }

    @Test
    void isValid_wrongSecret_false() throws Exception {
        String body = "{\"action\":\"completed\"}";
        String signature = "sha256=" + hmac(body, "wrong-secret");

        assertThat(verifier.isValid(body, signature)).isFalse();
    }

    @Test
    void isValid_tamperedBody_false() throws Exception {
        String signature = "sha256=" + hmac("{\"action\":\"completed\"}", SECRET);

        assertThat(verifier.isValid("{\"action\":\"tampered\"}", signature)).isFalse();
    }

    @Test
    void isValid_missingHeader_false() {
        assertThat(verifier.isValid("{}", null)).isFalse();
    }

    @Test
    void isValid_malformedHeader_false() {
        assertThat(verifier.isValid("{}", "not-a-valid-signature")).isFalse();
    }

    private String hmac(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
