package com.pkv.worker.consumer;

import com.pkv.common.config.KafkaConstants;
import com.pkv.source.domain.Source;
import com.pkv.source.domain.SourceStatus;
import com.pkv.source.repository.SourceRepository;
import com.pkv.worker.dto.ChunkedDocument;
import com.pkv.worker.dto.EmbeddingJobMessage;
import com.pkv.worker.dto.ParsedDocument;
import com.pkv.worker.service.DocumentParser;
import com.pkv.worker.service.EmbeddingService;
import com.pkv.worker.service.TextChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class EmbeddingPipelineConsumer {

    private final SourceRepository sourceRepository;
    private final DocumentParser documentParser;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;

    @KafkaListener(
            topics = KafkaConstants.EMBEDDING_JOB_TOPIC,
            containerFactory = KafkaConstants.EMBEDDING_CONTAINER_FACTORY
    )
    public void consume(EmbeddingJobMessage message) {
        log.info("임베딩 작업 수신: sourceId={}", message.sourceId());

        Source source = sourceRepository.findById(message.sourceId()).orElse(null);
        if (source == null) {
            log.warn("Source를 찾을 수 없음: sourceId={}", message.sourceId());
            return;
        }

        if (source.getStatus() == SourceStatus.UPLOADED) {
            source.startProcessing();
            sourceRepository.save(source);
        } else if (source.getStatus() != SourceStatus.PROCESSING) {
            log.info("이미 처리된 Source, 건너뜀: sourceId={}, status={}", message.sourceId(), source.getStatus());
            return;
        }

        executePipeline(message);

        source.complete();
        sourceRepository.save(source);
        log.info("임베딩 파이프라인 완료: sourceId={}", message.sourceId());
    }

    public void recoverFailedEmbedding(ConsumerRecord<?, ?> record, Exception exception) {
        EmbeddingJobMessage message = (EmbeddingJobMessage) record.value();
        log.error("임베딩 파이프라인 재시도 소진: sourceId={}", message.sourceId(), exception);

        sourceRepository.findById(message.sourceId()).ifPresent(source -> {
            if (source.getStatus() == SourceStatus.PROCESSING) {
                source.fail();
                sourceRepository.save(source);
                log.info("Source 상태를 FAILED로 변경: sourceId={}", message.sourceId());
            }
        });
    }

    private void executePipeline(EmbeddingJobMessage message) {
        embeddingService.deleteBySourceId(message.sourceId());

        ParsedDocument parsed = documentParser.parse(message.storagePath(), message.fileExtension());

        ChunkedDocument chunked = textChunker.chunk(
                parsed, message.sourceId(), message.memberId(), message.originalFileName());

        embeddingService.embed(chunked);
    }
}
