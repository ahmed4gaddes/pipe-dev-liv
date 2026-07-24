package com.pipedevliv.pipeline.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.util.HexFormat;

/**
 * Vérifie la signature HMAC-SHA256 que GitHub appose sur chaque webhook (header
 * X-Hub-Signature-256, format "sha256=<hex>"), calculée sur le corps brut de la requête avec
 * le secret configuré côté GitHub (github.webhook-secret / GH_WEBHOOK_SECRET). Même technique
 * de comparaison en temps constant que HeaderAuthenticationFilter.isValidGatewaySecret dans
 * common-lib, pour la même raison (éviter une attaque par mesure de timing).
 */
@Component
public class WebhookSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    private final String webhookSecret;

    public WebhookSignatureVerifier(@Value("${github.webhook-secret}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public boolean isValid(String rawBody, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith(PREFIX) || rawBody == null) {
            return false;
        }
        String provided = signatureHeader.substring(PREFIX.length());
        String expected = computeSignature(rawBody);
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private String computeSignature(String rawBody) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] hmacBytes = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Impossible de calculer la signature HMAC du webhook", ex);
        }
    }
}
