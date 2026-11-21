package dev.ragbot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class SlackSignatureTest {
    private static String sign(String secret, String ts, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        return "v0=" + HexFormat.of().formatHex(mac.doFinal(("v0:" + ts + ":" + body).getBytes()));
    }

    @Test void acceptsValidSignature() throws Exception {
        assertTrue(SlackSignature.verify("s3cret", "1000", "{}", sign("s3cret", "1000", "{}"), 1010));
    }
    @Test void rejectsTamperedBody() throws Exception {
        assertFalse(SlackSignature.verify("s3cret", "1000", "{\"x\":1}", sign("s3cret", "1000", "{}"), 1010));
    }
    @Test void rejectsReplayOlderThanFiveMinutes() throws Exception {
        assertFalse(SlackSignature.verify("s3cret", "1000", "{}", sign("s3cret", "1000", "{}"), 1400));
    }
    @Test void rejectsWhenSecretNotConfigured() {
        assertFalse(SlackSignature.verify("", "1000", "{}", "v0=abc", 1000));
    }
}
