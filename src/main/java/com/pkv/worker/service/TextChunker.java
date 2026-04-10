package com.pkv.worker.service;

import com.pkv.worker.dto.ChunkedDocument;
import com.pkv.worker.dto.ChunkedDocument.Chunk;
import com.pkv.worker.dto.ParsedDocument;
import com.pkv.worker.dto.ParsedDocument.PageOffset;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Profile("worker")
public class TextChunker {

    private final DocumentSplitter splitter;
    private final TokenCountEstimator tokenEstimator;
    private final int overlapTokens;

    public TextChunker(
            @Value("${pkv.chunking.max-tokens:512}") int maxTokens,
            @Value("${pkv.chunking.overlap-tokens:64}") int overlapTokens,
            @Value("${langchain4j.open-ai.embedding-model.model-name}") String embeddingModelName) {
        this.tokenEstimator = new OpenAiTokenCountEstimator(embeddingModelName);
        this.overlapTokens = overlapTokens;
        this.splitter = DocumentSplitters.recursive(maxTokens, overlapTokens, tokenEstimator);
    }

    public ChunkedDocument chunk(ParsedDocument parsedDocument, Long documentId, Long memberId, String fileName) {
        Document document = Document.from(parsedDocument.fullText());
        List<TextSegment> segments = splitter.split(document);

        List<Chunk> chunks = new ArrayList<>();
        int searchFrom = 0;
        for (int index = 0; index < segments.size(); index++) {
            TextSegment segment = segments.get(index);
            String segmentText = trimToSentenceBoundary(segment.text());
            String sourceChunkRef = documentId == null ? null : documentId + ":" + index;

            int charOffset = findCharOffset(parsedDocument.fullText(), segmentText, searchFrom);
            int pageNumber = resolvePageNumber(parsedDocument.pageOffsets(), charOffset);

            if (charOffset >= 0) {
                searchFrom = charOffset + 1;
            }

            chunks.add(new Chunk(segmentText, sourceChunkRef, documentId, memberId, fileName, pageNumber));
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

    /**
     * 청크가 문장 중간에서 잘린 경우, 오버랩 범위 내에서 가장 가까운 자연스러운 경계로 trim한다.
     */
    String trimToSentenceBoundary(String text) {
        String stripped = text.stripTrailing();
        if (stripped.isEmpty() || endsAtNaturalBoundary(stripped)) {
            return stripped;
        }

        // 1순위: 문장 경계 (. ! ? + 공백)에서 trim
        int pos = findLastSentenceEnd(stripped);
        if (pos > 0 && isWithinOverlap(stripped, pos)) {
            return stripped.substring(0, pos);
        }

        // 2순위: 줄바꿈 경계에서 trim (코드/테이블이 문장 뒤에 오는 경우)
        pos = findLastNewline(stripped);
        if (pos > 0 && isWithinOverlap(stripped, pos)) {
            return stripped.substring(0, pos).stripTrailing();
        }

        return stripped;
    }

    private boolean endsAtNaturalBoundary(String text) {
        char last = text.charAt(text.length() - 1);
        return ".!?})]".indexOf(last) >= 0;
    }

    private int findLastSentenceEnd(String text) {
        for (int i = text.length() - 2; i >= 0; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(text.charAt(i + 1))) {
                return i + 1;
            }
        }
        return -1;
    }

    private int findLastNewline(String text) {
        for (int i = text.length() - 2; i >= 0; i--) {
            if (text.charAt(i) == '\n') {
                return i + 1;
            }
        }
        return -1;
    }

    private boolean isWithinOverlap(String text, int boundaryPos) {
        return tokenEstimator.estimateTokenCountInText(text.substring(boundaryPos)) <= overlapTokens;
    }
}
