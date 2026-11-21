package dev.ragbot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/telegram")
public class TelegramController {
    private static final Logger log = LoggerFactory.getLogger(TelegramController.class);

    public record Update(Message message, CallbackQuery callback_query) {
        public record Message(long message_id, Chat chat, User from, String text) {}
        public record Chat(long id) {}
        public record User(String username, String first_name) {}
        public record CallbackQuery(String id, String data, Message message) {}
    }

    private final RagService rag;
    private final AppProps props;
    private final RestClient http;

    TelegramController(RagService rag, AppProps props, RestClient.Builder builder) {
        this.rag = rag; this.props = props;
        this.http = builder.baseUrl("https://api.telegram.org/bot" + props.telegram().token()).build();
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
                                        @RequestBody Update update) {
        if (!safeEquals(props.telegram().secret(), secret)) return ResponseEntity.status(401).build();
        // Telegram ждёт быстрый 200, поэтому работаем в виртуальном потоке.
        if (update.callback_query() != null) Thread.startVirtualThread(() -> feedback(update.callback_query()));
        else if (update.message() != null && update.message().text() != null) Thread.startVirtualThread(() -> handle(update.message()));
        return ResponseEntity.ok().build();
    }

    private void handle(Update.Message m) {
        long chatId = m.chat().id();
        try {
            if (m.text().startsWith("/start")) { call("sendMessage", Map.of("chat_id", chatId, "text", "Привет! Задайте вопрос — отвечу по базе знаний и укажу источники.")); return; }
            String user = m.from() == null ? "unknown" : (m.from().username() != null ? m.from().username() : m.from().first_name());
            var answer = rag.answer("telegram", String.valueOf(chatId), user, m.text());
            String text = answer.getAnswer().length() > 4000 ? answer.getAnswer().substring(0, 4000) + "…" : answer.getAnswer();
            long id = answer.getId();
            call("sendMessage", Map.of("chat_id", chatId, "text", text, "reply_markup", Map.of("inline_keyboard", List.of(List.of(
                    Map.of("text", "👍", "callback_data", "fb:" + id + ":1"),
                    Map.of("text", "👎", "callback_data", "fb:" + id + ":-1"))))));
        } catch (Exception e) {
            log.error("Telegram handler failed", e);
            call("sendMessage", Map.of("chat_id", chatId, "text", "Что-то пошло не так, попробуйте позже."));
        }
    }

    private void feedback(Update.CallbackQuery q) {
        try {
            String[] p = q.data().split(":");                       // fb:<messageLogId>:<1|-1>
            if (p.length != 3 || !p[0].equals("fb")) return;
            int value = Integer.parseInt(p[2]);
            rag.rate(Long.parseLong(p[1]), value);
            call("answerCallbackQuery", Map.of("callback_query_id", q.id(), "text", value > 0 ? "Спасибо за 👍" : "Спасибо, учтём 👎"));
            call("editMessageReplyMarkup", Map.of("chat_id", q.message().chat().id(), "message_id", q.message().message_id(),
                    "reply_markup", Map.of("inline_keyboard", List.of())));   // убираем кнопки
        } catch (Exception e) { log.warn("Feedback failed", e); }
    }

    private void call(String method, Map<String, Object> body) {
        http.post().uri("/" + method).body(body).retrieve().toBodilessEntity();
    }

    private static boolean safeEquals(String a, String b) {
        return b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
