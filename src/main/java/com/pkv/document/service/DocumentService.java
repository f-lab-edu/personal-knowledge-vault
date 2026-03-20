package com.pkv.document.service;

import com.pkv.chat.repository.TurnCitationRepository;
import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import com.pkv.common.service.EmbeddingRepository;
import com.pkv.document.domain.Document;
import com.pkv.document.domain.DocumentStatus;
import com.pkv.document.dto.DocumentResponse;
import com.pkv.document.dto.PresignRequest;
import com.pkv.document.dto.PresignResponse;
import com.pkv.document.repository.DocumentRepository;
import com.pkv.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Profile("api")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentValidator documentValidator;
    private final S3FileStorage s3FileStorage;
    private final EmbeddingJobProducer embeddingJobProducer;
    private final EmbeddingRepository embeddingRepository;
    private final TurnCitationRepository turnCitationRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public PresignResponse requestPresignedUrl(Long memberId, PresignRequest request) {
        validateMemberExists(memberId);
        String fileName = request.fileName();
        long fileSize = request.fileSize();

        int dotIndex = fileName.lastIndexOf('.');
        String name = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
        String extension = (dotIndex > 0) ? fileName.substring(dotIndex + 1).toLowerCase() : "";

        documentValidator.validateDocumentName(name);
        documentValidator.validateExtension(extension);
        documentValidator.validateFileSize(fileSize);

        long currentCount = documentRepository.countByMemberIdAndStatusNot(memberId, DocumentStatus.INITIATED);
        documentValidator.validateDocumentCount(currentCount);

        long currentTotal = documentRepository.sumFileSizeByMemberIdAndStatusNot(memberId, DocumentStatus.INITIATED);
        documentValidator.validateTotalSize(currentTotal, fileSize);

        boolean duplicate = documentRepository.existsByMemberIdAndOriginalFileNameAndStatusNot(
                memberId, fileName, DocumentStatus.INITIATED);
        documentValidator.validateDuplicateDocumentName(duplicate);

        documentRepository.deleteByMemberIdAndOriginalFileNameAndStatus(memberId, fileName, DocumentStatus.INITIATED);

        Document document = Document.builder()
                .memberId(memberId)
                .originalFileName(fileName)
                .fileSize(fileSize)
                .fileExtension(extension)
                .status(DocumentStatus.INITIATED)
                .build();
        documentRepository.save(document);

        String key = "documents/" + System.currentTimeMillis() + "_" + UUID.randomUUID() + "." + extension;
        document.assignStoragePath(key);

        String contentType = documentValidator.getContentType(extension);
        S3FileStorage.PresignedUploadUrl presigned = s3FileStorage.generatePresignedPutUrl(key, contentType, fileSize);

        return new PresignResponse(
                document.getId(),
                presigned.url(),
                presigned.expiresAt()
        );
    }

    public List<DocumentResponse> getDocuments(Long memberId) {
        return documentRepository.findByMemberIdAndStatusNotOrderByCreatedAtDesc(memberId, DocumentStatus.INITIATED)
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Transactional
    public DocumentResponse confirmUpload(Long memberId, Long documentId) {
        validateMemberExists(memberId);
        Document document = documentRepository.findByIdAndMemberId(documentId, memberId)
                .orElseThrow(() -> new PkvException(ErrorCode.DOCUMENT_NOT_FOUND));

        if (document.getStatus() != DocumentStatus.INITIATED) {
            throw new PkvException(ErrorCode.DOCUMENT_UPLOAD_NOT_CONFIRMED);
        }

        if (!s3FileStorage.doesObjectExist(document.getStoragePath())) {
            throw new PkvException(ErrorCode.DOCUMENT_UPLOAD_NOT_CONFIRMED);
        }

        document.confirm();
        embeddingJobProducer.send(document);

        return DocumentResponse.from(document);
    }

    @Transactional
    public void deleteDocument(Long memberId, Long documentId) {
        validateMemberExists(memberId);
        Document document = documentRepository.findByIdAndMemberId(documentId, memberId)
                .orElseThrow(() -> new PkvException(ErrorCode.DOCUMENT_NOT_FOUND));

        if (!document.isDeletable()) {
            throw new PkvException(ErrorCode.DOCUMENT_DELETE_NOT_ALLOWED);
        }

        turnCitationRepository.clearDocumentIdByDocumentId(documentId);
        embeddingRepository.deleteByDocumentId(documentId);
        s3FileStorage.deleteObject(document.getStoragePath());
        documentRepository.delete(document);
    }

    private void validateMemberExists(Long memberId) {
        if (!memberRepository.existsByIdAndDeletedAtIsNull(memberId)) {
            throw new PkvException(ErrorCode.MEMBER_NOT_FOUND);
        }
    }
}
