package com.pkv.worker.consumer;

import com.pkv.common.config.KafkaConstants;
import com.pkv.common.service.EmbeddingRepository;
import com.pkv.document.domain.Document;
import com.pkv.document.domain.DocumentStatus;
import com.pkv.document.dto.EmbeddingJobMessage;
import com.pkv.document.repository.DocumentRepository;
import com.pkv.worker.dto.ChunkedDocument;
import com.pkv.worker.dto.ParsedDocument;
import com.pkv.worker.service.DocumentParser;
import com.pkv.worker.service.EmbeddingService;
import com.pkv.worker.service.TextChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class EmbeddingPipelineConsumer {

    private final DocumentRepository documentRepository;
    private final EmbeddingRepository embeddingRepository;
    private final DocumentParser documentParser;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;

    @KafkaListener(
            topics = KafkaConstants.EMBEDDING_JOB_TOPIC,
            containerFactory = KafkaConstants.EMBEDDING_CONTAINER_FACTORY
    )
    public void consume(EmbeddingJobMessage message) {
        log.info("임베딩 작업 수신: documentId={}", message.documentId());

        Document document = documentRepository.findById(message.documentId()).orElse(null);
        if (document == null) {
            log.warn("Document를 찾을 수 없음: documentId={}", message.documentId());
            return;
        }

        if (document.getStatus() == DocumentStatus.UPLOADED) {
            document.startProcessing();
            documentRepository.save(document);
        } else if (document.getStatus() != DocumentStatus.PROCESSING) {
            log.info("이미 처리된 Document, 건너뜀: documentId={}, status={}", message.documentId(), document.getStatus());
            return;
        }

        executePipeline(message);

        document.complete();
        documentRepository.save(document);
        log.info("임베딩 파이프라인 완료: documentId={}", message.documentId());
    }

    private void executePipeline(EmbeddingJobMessage message) {
        embeddingRepository.deleteByDocumentId(message.documentId());

        ParsedDocument parsed = documentParser.parse(message.storagePath(), message.fileExtension());
        ChunkedDocument chunked = textChunker.chunk(
                parsed,
                message.documentId(),
                message.memberId(),
                message.originalFileName()
        );

        embeddingService.embed(chunked);
    }
}
