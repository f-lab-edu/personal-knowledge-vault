package com.pkv.worker.service;

import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import com.pkv.worker.dto.ChunkedDocument;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import dev.langchain4j.store.embedding.filter.Filter;

import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
@Service
@Profile("worker")
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    private final EmbeddingStore<TextSegment> embeddingStore;

    public void deleteBySourceId(Long sourceId) {
        Filter filter = metadataKey("sourceId").isEqualTo(sourceId);
        embeddingStore.removeAll(filter);
        log.info("기존 벡터 삭제 완료: sourceId={}", sourceId);
    }

    public void embed(ChunkedDocument chunkedDocument) {
        List<TextSegment> segments = chunkedDocument.chunks().stream()
                .map(this::toTextSegment)
                .toList();

        try {
            Response<List<Embedding>> response = embeddingModel.embedAll(segments);
            List<Embedding> embeddings = response.content();
            embeddingStore.addAll(embeddings, segments);
            log.info("임베딩 완료: {}개 청크 처리", segments.size());
        } catch (Exception e) {
            throw new PkvException(ErrorCode.EMBEDDING_FAILED, e);
        }
    }

    private TextSegment toTextSegment(ChunkedDocument.Chunk chunk) {
        Metadata metadata = new Metadata()
                .put("memberId", chunk.memberId())
                .put("sourceId", chunk.sourceId())
                .put("fileName", chunk.fileName())
                .put("pageNumber", chunk.pageNumber());
        return TextSegment.from(chunk.text(), metadata);
    }
}
