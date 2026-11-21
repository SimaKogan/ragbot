package dev.ragbot;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Getter @Setter
public class MessageLog {
    public record SourceRef(String doc, String snippet, Double score) {}

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String channel, chatId, username;
    @Column(columnDefinition = "text") private String question;
    @Column(columnDefinition = "text") private String answer;
    private long latencyMs;
    /** 1 = 👍, -1 = 👎, null = нет оценки */
    private Integer feedback;
    private Instant createdAt = Instant.now();
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private List<SourceRef> sources = List.of();
}
