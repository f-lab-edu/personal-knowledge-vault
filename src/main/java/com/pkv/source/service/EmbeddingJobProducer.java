package com.pkv.source.service;

import com.pkv.common.config.KafkaConstants;
import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import com.pkv.source.domain.Source;
import com.pkv.source.dto.EmbeddingJobMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingJobProducer {

    private final KafkaTemplate<String, EmbeddingJobMessage> kafkaTemplate;

    public void send(Source source) {
        EmbeddingJobMessage message = new EmbeddingJobMessage(
                source.getId(),
                source.getMemberId(),
                source.getStoragePath(),
                source.getOriginalFileName(),
                source.getFileExtension()
        );

        try {
            kafkaTemplate.send(
                    KafkaConstants.EMBEDDING_JOB_TOPIC,
                    source.getId().toString(),
                    message
            ).get(5, TimeUnit.SECONDS);

            log.info("임베딩 작업 메시지 발행 완료: sourceId={}", source.getId());
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new PkvException(ErrorCode.EMBEDDING_JOB_PUBLISH_FAILED, e);
        }
    }
}
