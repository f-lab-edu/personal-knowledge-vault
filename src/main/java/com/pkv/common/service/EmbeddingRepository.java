package com.pkv.common.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
@Repository
@RequiredArgsConstructor
public class EmbeddingRepository {

    private final EmbeddingStore<TextSegment> embeddingStore;

    public void deleteBySourceId(Long sourceId) {
        Filter filter = metadataKey("sourceId").isEqualTo(sourceId);
        embeddingStore.removeAll(filter);
        log.info("벡터 삭제 완료: sourceId={}", sourceId);
    }
}
