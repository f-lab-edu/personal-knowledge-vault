package com.pkv.document.service;

import com.pkv.common.config.KafkaConstants;
import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import com.pkv.document.domain.Document;
import com.pkv.document.dto.EmbeddingJobMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@Profile("api")
@RequiredArgsConstructor
public class EmbeddingJobProducer {

    private final KafkaTemplate<String, EmbeddingJobMessage> kafkaTemplate;

    public void send(Document document) {
        EmbeddingJobMessage message = new EmbeddingJobMessage(
                document.getId(),
                document.getMemberId(),
                document.getStoragePath(),
                document.getOriginalFileName(),
                document.getFileExtension()
        );

        try {
            kafkaTemplate.send(
                    KafkaConstants.EMBEDDING_JOB_TOPIC,
                    document.getId().toString(),
                    message
            ).get(5, TimeUnit.SECONDS);

            log.info("임베딩 작업 메시지 발행 완료: documentId={}", document.getId());
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new PkvException(ErrorCode.EMBEDDING_JOB_PUBLISH_FAILED, e);
        }
    }
}
