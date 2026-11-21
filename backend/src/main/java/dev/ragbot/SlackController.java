package dev.ragbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

/** Slack Events API (app_mention, message.im) + Interactivity (кнопки 👍/👎). */
@RestController
@RequestMapping("/api/slack")
public class SlackController {
    private static final Logger log = LoggerFactory.getLogger(SlackController.class);

    private static final java.util.concurrent.Executor ASYNC = java.util.concurrent.Executors.newFixedThreadPool(16);
    private final RagService rag;
    private final AppProps props;
    private final ObjectMapper json;
    private final RestClient http;

    SlackController(RagService rag, AppProps props, ObjectMapper json, RestClient.Builder builder) {
        this.rag = rag; this.props = props; this.json = json;
        this.http = builder.baseUrl("https://slack.com/api").build();
    }

    @PostMapping("/events")
    public ResponseEntity<Object> events(@RequestHeader(value = "X-Slack-Request-Timestamp", required = false) String ts,
                                         @RequestHeader(value = "X-Slack-Signature", required = false) String sig,
                                         @RequestHeader(value = "X-Slack-Retry-Num", required = false) String retry,
                                         @RequestBody String body) throws Exception {
        if (!verified(ts, sig, body)) return ResponseEntity.status(401).build();
        JsonNode root = json.readTree(body);
        if ("url_verification".equals(root.path("type").asText()))
            return ResponseEntity.ok(Map.of("challenge", root.path("challenge").asText()));
        if (retry != null) return ResponseEntity.ok().build();      // ретрай из-за медленного ack — уже обрабатываем
        JsonNode e = root.path("event");
        String type = e.path("type").asText();
        boolean dm = "message".equals(type) && "im".equals(e.path("channel_type").asText())
                && !e.has("bot_id") && !e.has("subtype");
        if (dm || "app_mention".equals(type)) ASYNC.execute(() -> handle(e));  // Slack требует ack < 3 c
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/interactions", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<Void> interactions(@RequestHeader(value = "X-Slack-Request-Timestamp", required = false) String ts,
                                             @RequestHeader(value = "X-Slack-Signature", required = false) String sig,
                                             @RequestBody String body) throws Exception {
        if (!verified(ts, sig, body)) return ResponseEntity.status(401).build();
        String raw = Arrays.stream(body.split("&")).filter(p -> p.startsWith("payload="))
                .findFirst().map(p -> URLDecoder.decode(p.substring(8), StandardCharsets.UTF_8)).orElse("{}");
        JsonNode p = json.readTree(raw);
        JsonNode action = p.path("actions").path(0);
        String actionId = action.path("action_id").asText();
        if (actionId.startsWith("feedback_")) {
            boolean up = actionId.equals("feedback_up");
            try {
                rag.rate(action.path("value").asLong(), up ? 1 : -1);
                var blocks = List.<Object>of(p.path("message").path("blocks").path(0), Map.of("type", "context",
                        "elements", List.of(Map.of("type", "mrkdwn", "text", up ? "👍 Спасибо за оценку!" : "👎 Спасибо, учтём!"))));
                http.post().uri(URI.create(p.path("response_url").asText()))
                        .body(Map.of("replace_original", true, "text", p.path("message").path("text").asText(), "blocks", blocks))
                        .retrieve().toBodilessEntity();                // убираем кнопки
            } catch (Exception ex) { log.warn("Slack feedback failed", ex); }
        }
        return ResponseEntity.ok().build();
    }

    private void handle(JsonNode e) {
        String channel = e.path("channel").asText();
        String text = e.path("text").asText().replaceAll("<@[A-Z0-9]+>", "").trim();
        String thread = e.hasNonNull("thread_ts") ? e.get("thread_ts").asText() : e.path("ts").asText();
        if (text.isBlank()) return;
        try {
            var m = rag.answer("slack", channel, e.path("user").asText(), text);
            String answer = mrkdwn(m.getAnswer());
            var blocks = List.of(
                    Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", answer)),
                    Map.of("type", "actions", "block_id", "feedback", "elements", List.of(
                            button("👍", "feedback_up", m.getId()), button("👎", "feedback_down", m.getId()))));
            post(Map.of("channel", channel, "thread_ts", thread, "text", answer, "blocks", blocks));
        } catch (Exception ex) {
            log.error("Slack handler failed", ex);
            post(Map.of("channel", channel, "thread_ts", thread, "text", "Что-то пошло не так, попробуйте позже."));
        }
    }

    private void post(Map<String, Object> body) {
        http.post().uri("/chat.postMessage").header("Authorization", "Bearer " + props.slack().botToken())
                .body(body).retrieve().toBodilessEntity();
    }

    private static Map<String, Object> button(String label, String actionId, Long id) {
        return Map.of("type", "button", "text", Map.of("type", "plain_text", "text", label),
                "action_id", actionId, "value", String.valueOf(id));
    }

    /** Gemini отвечает markdown'ом; Slack mrkdwn использует *жирный*. Лимит section — 3000 символов. */
    private static String mrkdwn(String s) {
        String r = s.replaceAll("\\*\\*(.+?)\\*\\*", "*$1*");
        return r.length() > 2900 ? r.substring(0, 2900) + "…" : r;
    }

    private boolean verified(String ts, String sig, String body) {
        return SlackSignature.verify(props.slack().signingSecret(), ts, body, sig, Instant.now().getEpochSecond());
    }
}
