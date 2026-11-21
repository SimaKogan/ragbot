package dev.ragbot;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Проверка подписи Slack: v0=HMAC_SHA256("v0:{timestamp}:{rawBody}"), окно 5 минут против replay-атак. */
final class SlackSignature {
    private SlackSignature() {}

    static boolean verify(String secret, String timestamp, String body, String signature, long nowEpochSec) {
        if (secret == null || secret.isBlank() || timestamp == null || signature == null) return false;
        try {
            if (Math.abs(nowEpochSec - Long.parseLong(timestamp)) > 300) return false;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "v0=" + HexFormat.of().formatHex(mac.doFinal(("v0:" + timestamp + ":" + body).getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (NumberFormatException | GeneralSecurityException e) {
            return false;
        }
    }
}
