package com.pkv.worker.service;

import com.pkv.worker.dto.ChunkedDocument;
import com.pkv.worker.dto.ChunkedDocument.Chunk;
import com.pkv.worker.dto.ParsedDocument;
import com.pkv.worker.dto.ParsedDocument.PageOffset;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("worker")
public class TextChunker {

    private final DocumentSplitter splitter;

    public TextChunker(
            @Value("${pkv.chunking.max-tokens:512}") int maxTokens,
            @Value("${pkv.chunking.overlap-tokens:64}") int overlapTokens,
            @Value("${langchain4j.open-ai.embedding-model.model-name}") String embeddingModelName) {
        this.splitter = DocumentSplitters.recursive(
                maxTokens, overlapTokens, new OpenAiTokenCountEstimator(embeddingModelName)
        );
    }
    
    public ChunkedDocument chunk(ParsedDocument parsedDocument, Long sourceId, Long memberId,
                                  String fileName) {
        Document document = Document.from(parsedDocument.fullText());
        List<TextSegment> segments = splitter.split(document);

        List<Chunk> chunks = new ArrayList<>();
        int searchFrom = 0;
        for (TextSegment segment : segments) {
            String segmentText = segment.text();

            int charOffset = findCharOffset(parsedDocument.fullText(), segmentText, searchFrom);
            int pageNumber = resolvePageNumber(parsedDocument.pageOffsets(), charOffset);

            if (charOffset >= 0) {
                searchFrom = charOffset + 1;
            }

            chunks.add(new Chunk(segmentText, sourceId, memberId, fileName, pageNumber));
        }

        return new ChunkedDocument(chunks);
    }

    private int findCharOffset(String fullText, String segmentText, int searchFrom) {
        int charOffset = fullText.indexOf(segmentText, searchFrom);
        return charOffset >= 0 ? charOffset : fullText.indexOf(segmentText);
    }

    private int resolvePageNumber(List<PageOffset> pageOffsets, int charOffset) {
        if (charOffset < 0) {
            return pageOffsets.getLast().pageNumber();
        }

        return pageOffsets.stream()
                .filter(page -> page.startOffset() <= charOffset)
                .reduce((previous, current) -> current)
                .map(PageOffset::pageNumber)
                .orElse(pageOffsets.getFirst().pageNumber());
    }
}
