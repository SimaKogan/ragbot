package dev.ragbot;

import dev.ragbot.MessageLog.SourceRef;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ApiController {
    public record ChatRequest(String question) {}
    public record FeedbackRequest(int value) {}
    public record DocQuality(String doc, long up, long down) {}

    private final MessageLogRepo messages;
    private final KnowledgeDocRepo docs;
    private final KnowledgeService knowledge;
    private final RagService rag;

    ApiController(MessageLogRepo m, KnowledgeDocRepo d, KnowledgeService k, RagService r) {
        messages = m; docs = d; knowledge = k; rag = r;
    }

    /** feedback=1 → только 👍, feedback=-1 → только 👎 (очередь на разбор), без параметра — все. */
    @GetMapping("/messages")
    public Page<MessageLog> messages(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) Integer feedback) {
        var pr = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return feedback == null ? messages.findAll(pr) : messages.findByFeedback(feedback, pr);
    }

    @PostMapping("/messages/{id}/feedback")
    public MessageLog feedback(@PathVariable Long id, @RequestBody FeedbackRequest r) { return rag.rate(id, r.value()); }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        var week = messages.findByCreatedAtAfter(Instant.now().minus(Duration.ofDays(7)));
        var dayAgo = Instant.now().minus(Duration.ofDays(1));
        var perDay = new TreeMap<String, Long>();
        for (int i = 6; i >= 0; i--) perDay.put(Instant.now().minus(Duration.ofDays(i)).atZone(ZoneOffset.UTC).toLocalDate().toString(), 0L);
        week.forEach(m -> perDay.merge(m.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate().toString(), 1L, Long::sum));

        long up = week.stream().filter(m -> Objects.equals(m.getFeedback(), 1)).count();
        long down = week.stream().filter(m -> Objects.equals(m.getFeedback(), -1)).count();
        // качество по документам: в каких источниках чаще стоят 👎
        var perDoc = new TreeMap<String, long[]>();
        for (var m : week) {
            if (m.getFeedback() == null) continue;
            m.getSources().stream().map(SourceRef::doc).distinct()
                    .forEach(d -> perDoc.computeIfAbsent(d, k -> new long[2])[m.getFeedback() > 0 ? 0 : 1]++);
        }
        var quality = perDoc.entrySet().stream().map(e -> new DocQuality(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(DocQuality::down).reversed()).toList();

        var out = new HashMap<String, Object>();
        out.put("total", messages.count());
        out.put("last24h", week.stream().filter(m -> m.getCreatedAt().isAfter(dayAgo)).count());
        out.put("avgLatencyMs", Math.round(week.stream().mapToLong(MessageLog::getLatencyMs).average().orElse(0)));
        out.put("groundedRate", week.isEmpty() ? 0 : Math.round(100.0 * week.stream().filter(m -> !m.getSources().isEmpty()).count() / week.size()));
        out.put("documents", docs.count());
        out.put("perDay", perDay);
        out.put("thumbsUp", up);
        out.put("thumbsDown", down);
        out.put("satisfaction", up + down == 0 ? null : Math.round(100.0 * up / (up + down)));
        out.put("sourceQuality", quality);
        return out;
    }

    @GetMapping("/documents")
    public List<KnowledgeDoc> documents() { return docs.findAll(Sort.by(Sort.Direction.DESC, "createdAt")); }

    @PostMapping("/documents")
    public KnowledgeDoc upload(@RequestParam("file") MultipartFile file) throws IOException { return knowledge.ingest(file); }

    @DeleteMapping("/documents/{id}")
    public void delete(@PathVariable Long id) { knowledge.delete(id); }

    /** Песочница: тот же RAG-пайплайн без мессенджера. */
    @PostMapping("/chat")
    public MessageLog chat(@RequestBody ChatRequest r) { return rag.answer("web", "playground", "admin", r.question()); }
}
