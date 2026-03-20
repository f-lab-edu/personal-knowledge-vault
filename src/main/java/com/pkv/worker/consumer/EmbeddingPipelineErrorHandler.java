package com.pkv.worker.consumer;

import com.pkv.document.dto.EmbeddingJobMessage;
import com.pkv.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class EmbeddingPipelineErrorHandler {

    private final DocumentRepository documentRepository;

    public void recoverFailedEmbedding(ConsumerRecord<?, ?> record, Exception exception) {
        EmbeddingJobMessage message = (EmbeddingJobMessage) record.value();
        log.error("임베딩 파이프라인 재시도 소진: documentId={}", message.documentId(), exception);

        documentRepository.findById(message.documentId()).ifPresent(document -> {
            try {
                document.fail();
            } catch (IllegalStateException ignored) {
                return;
            }
            documentRepository.save(document);
            log.info("Document 상태를 FAILED로 변경: documentId={}", message.documentId());
        });
    }
}
