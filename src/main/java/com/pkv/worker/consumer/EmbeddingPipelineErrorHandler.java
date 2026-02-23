package com.pkv.worker.consumer;

import com.pkv.source.dto.EmbeddingJobMessage;
import com.pkv.source.repository.SourceRepository;
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

    private final SourceRepository sourceRepository;

    public void recoverFailedEmbedding(ConsumerRecord<?, ?> record, Exception exception) {
        EmbeddingJobMessage message = (EmbeddingJobMessage) record.value();
        log.error("임베딩 파이프라인 재시도 소진: sourceId={}", message.sourceId(), exception);

        sourceRepository.findById(message.sourceId()).ifPresent(source -> {
            try {
                source.fail();
            } catch (IllegalStateException ignored) {
                return;
            }
            sourceRepository.save(source);
            log.info("Source 상태를 FAILED로 변경: sourceId={}", message.sourceId());
        });
    }
}
