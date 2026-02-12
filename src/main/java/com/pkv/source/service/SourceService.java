package com.pkv.source.service;

import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import com.pkv.source.domain.Source;
import com.pkv.source.domain.SourceStatus;
import com.pkv.source.dto.PresignRequest;
import com.pkv.source.dto.PresignResponse;
import com.pkv.source.dto.SourceResponse;
import com.pkv.source.repository.SourceRepository;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SourceService {

    private final SourceRepository sourceRepository;
    private final SourceValidator sourceValidator;
    private final S3FileStorage s3FileStorage;
    private final EmbeddingJobProducer embeddingJobProducer;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Transactional
    public PresignResponse requestPresignedUrl(Long memberId, PresignRequest request) {
        String fileName = request.fileName();
        long fileSize = request.fileSize();

        int dotIndex = fileName.lastIndexOf('.');
        String name = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
        String extension = (dotIndex > 0) ? fileName.substring(dotIndex + 1).toLowerCase() : "";

        sourceValidator.validateSourceName(name);
        sourceValidator.validateExtension(extension);
        sourceValidator.validateFileSize(fileSize);

        long currentCount = sourceRepository.countByMemberIdAndStatusNot(memberId, SourceStatus.INITIATED);
        sourceValidator.validateSourceCount(currentCount);

        long currentTotal = sourceRepository.sumFileSizeByMemberIdAndStatusNot(memberId, SourceStatus.INITIATED);
        sourceValidator.validateTotalSize(currentTotal, fileSize);

        boolean duplicate = sourceRepository.existsByMemberIdAndOriginalFileNameAndStatusNot(
                memberId, fileName, SourceStatus.INITIATED);
        sourceValidator.validateDuplicateSourceName(duplicate);

        sourceRepository.deleteByMemberIdAndOriginalFileNameAndStatus(
                memberId, fileName, SourceStatus.INITIATED);

        Source source = Source.builder()
                .memberId(memberId)
                .originalFileName(fileName)
                .fileSize(fileSize)
                .fileExtension(extension)
                .status(SourceStatus.INITIATED)
                .build();
        sourceRepository.save(source);

        String key = "sources/" + System.currentTimeMillis() + "_" + UUID.randomUUID() + "." + extension;
        source.assignStoragePath(key);

        String contentType = sourceValidator.getContentType(extension);
        S3FileStorage.PresignedUploadUrl presigned = s3FileStorage.generatePresignedPutUrl(key, contentType, fileSize);

        return new PresignResponse(
                source.getId(),
                presigned.url(),
                presigned.expiresAt()
        );
    }

    public List<SourceResponse> getSources(Long memberId) {
        return sourceRepository.findByMemberIdAndStatusNotOrderByCreatedAtDesc(memberId, SourceStatus.INITIATED)
                .stream().map(SourceResponse::from).toList();
    }

    @Transactional
    public SourceResponse confirmUpload(Long memberId, Long sourceId) {
        Source source = sourceRepository.findByIdAndMemberId(sourceId, memberId)
                .orElseThrow(() -> new PkvException(ErrorCode.SOURCE_NOT_FOUND));

        if (source.getStatus() != SourceStatus.INITIATED) {
            throw new PkvException(ErrorCode.SOURCE_UPLOAD_NOT_CONFIRMED);
        }

        if (!s3FileStorage.doesObjectExist(source.getStoragePath())) {
            throw new PkvException(ErrorCode.SOURCE_UPLOAD_NOT_CONFIRMED);
        }

        source.confirm();
        embeddingJobProducer.send(source);

        return SourceResponse.from(source);
    }

    @Transactional
    public void deleteSource(Long memberId, Long sourceId) {
        Source source = sourceRepository.findByIdAndMemberId(sourceId, memberId)
                .orElseThrow(() -> new PkvException(ErrorCode.SOURCE_NOT_FOUND));

        if (!source.isDeletable()) {
            throw new PkvException(ErrorCode.SOURCE_DELETE_NOT_ALLOWED);
        }

        Filter filter = metadataKey("sourceId").isEqualTo(sourceId);
        embeddingStore.removeAll(filter);

        s3FileStorage.deleteObject(source.getStoragePath());
        sourceRepository.delete(source);
    }
}
