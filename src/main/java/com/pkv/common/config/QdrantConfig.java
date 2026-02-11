package com.pkv.common.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class QdrantConfig {

    private static final int VECTOR_DIMENSION = 1536;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            @Value("${qdrant.host}") String host,
            @Value("${qdrant.port}") int port,
            @Value("${qdrant.collection-name}") String collectionName) {
        ensureCollectionExists(host, port, collectionName);
        return QdrantEmbeddingStore.builder()
                .host(host)
                .port(port)
                .collectionName(collectionName)
                .build();
    }

    private void ensureCollectionExists(String host, int port, String collectionName) {
        try (QdrantClient client = new QdrantClient(
                QdrantGrpcClient.newBuilder(host, port, false).build())) {
            if (!client.collectionExistsAsync(collectionName).get()) {
                client.createCollectionAsync(collectionName,
                        VectorParams.newBuilder()
                                .setSize(VECTOR_DIMENSION)
                                .setDistance(Distance.Cosine)
                                .build()
                ).get();
                log.info("Qdrant 컬렉션 '{}' 생성 완료", collectionName);
            }
        } catch (Exception e) {
            log.warn("Qdrant 컬렉션 초기화 실패 — Qdrant가 실행 중인지 확인하세요: {}", e.getMessage());
        }
    }
}
