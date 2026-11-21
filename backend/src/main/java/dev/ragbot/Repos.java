package dev.ragbot;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface MessageLogRepo extends JpaRepository<MessageLog, Long> {
    List<MessageLog> findByCreatedAtAfter(Instant since);
    Page<MessageLog> findByFeedback(Integer feedback, Pageable pageable);
}

interface KnowledgeDocRepo extends JpaRepository<KnowledgeDoc, Long> {}
