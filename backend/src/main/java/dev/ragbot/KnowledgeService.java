package dev.ragbot;

import java.io.IOException;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeService {
    private final VectorStore store;
    private final KnowledgeDocRepo repo;

    KnowledgeService(VectorStore store, KnowledgeDocRepo repo) { this.store = store; this.repo = repo; }

    /** PDF / DOCX / MD / TXT → чанки → эмбеддинги Vertex AI → pgvector. */
    public KnowledgeDoc ingest(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        List<Document> chunks = new TokenTextSplitter()
                .apply(new TikaDocumentReader(new ByteArrayResource(file.getBytes())).get());
        chunks.forEach(c -> c.getMetadata().put("source", name));
        store.add(chunks);
        var doc = new KnowledgeDoc();
        doc.setFilename(name); doc.setChunks(chunks.size());
        doc.setChunkIds(chunks.stream().map(Document::getId).toList());
        return repo.save(doc);
    }

    public void delete(Long id) {
        repo.findById(id).ifPresent(d -> { store.delete(d.getChunkIds()); repo.delete(d); });
    }
}
