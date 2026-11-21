package dev.ragbot;

import dev.ragbot.MessageLog.SourceRef;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class RagService {
    private static final String SYSTEM = """
            Ты — ассистент поддержки. Отвечай только на основе блока Context.
            Если в контексте нет ответа, честно скажи об этом и не выдумывай.
            Отвечай на языке вопроса, кратко и по делу. Ссылайся на источники в виде [имя_файла].""";
    private static final String NO_CONTEXT = "В базе знаний нет информации по этому вопросу. Попробуйте переформулировать или обратитесь к оператору.";

    private final ChatClient chat;
    private final VectorStore store;
    private final MessageLogRepo repo;
    private final AppProps props;

    RagService(ChatClient.Builder builder, VectorStore store, MessageLogRepo repo, AppProps props) {
        this.chat = builder.defaultSystem(SYSTEM).build();
        this.store = store; this.repo = repo; this.props = props;
    }

    public MessageLog answer(String channel, String chatId, String user, String question) {
        long t0 = System.currentTimeMillis();
        List<Document> docs = store.similaritySearch(SearchRequest.builder().query(question)
                .topK(props.rag().topK()).similarityThreshold(props.rag().threshold()).build());
        String answer = NO_CONTEXT;
        if (!docs.isEmpty()) {
            String context = docs.stream()
                    .map(d -> "[" + d.getMetadata().get("source") + "] " + d.getText())
                    .collect(Collectors.joining("\n---\n"));
            answer = chat.prompt()
                    .user(u -> u.text("Context:\n{context}\n\nВопрос: {q}").param("context", context).param("q", question))
                    .call().content();
        }
        var log = new MessageLog();
        log.setChannel(channel); log.setChatId(chatId); log.setUsername(user);
        log.setQuestion(question); log.setAnswer(answer);
        log.setSources(docs.stream().map(d -> new SourceRef(
                String.valueOf(d.getMetadata().get("source")), snippet(d.getText()), d.getScore())).toList());
        log.setLatencyMs(System.currentTimeMillis() - t0);
        return repo.save(log);
    }

    public MessageLog rate(Long id, int value) {
        var m = repo.findById(id).orElseThrow();
        m.setFeedback(value > 0 ? 1 : -1);
        return repo.save(m);
    }

    private static String snippet(String s) { return s.length() > 240 ? s.substring(0, 240) + "…" : s; }
}
