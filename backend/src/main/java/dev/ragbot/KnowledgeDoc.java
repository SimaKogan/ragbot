package dev.ragbot;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Getter @Setter
public class KnowledgeDoc {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String filename;
    private int chunks;
    private Instant createdAt = Instant.now();
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private List<String> chunkIds = List.of();
}
