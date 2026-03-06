package com.pkv.document.service;

import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import com.pkv.common.service.EmbeddingRepository;
import com.pkv.document.domain.Document;
import com.pkv.document.domain.DocumentStatus;
import com.pkv.document.dto.DocumentResponse;
import com.pkv.document.dto.PresignRequest;
import com.pkv.document.dto.PresignResponse;
import com.pkv.document.repository.DocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentValidator documentValidator;

    @Mock
    private S3FileStorage s3FileStorage;

    @Mock
    private EmbeddingJobProducer embeddingJobProducer;

    @Mock
    private EmbeddingRepository embeddingRepository;

    @InjectMocks
    private DocumentService documentService;

    @Test
    @DisplayName("유효한 파일 정보로 요청하면 Presigned URL과 documentId를 반환한다")
    void requestPresignedUrlSuccess() {
        PresignRequest request = new PresignRequest("설계서.pdf", 1024L);

        given(documentRepository.countByMemberIdAndStatusNot(anyLong(), any())).willReturn(0L);
        given(documentRepository.sumFileSizeByMemberIdAndStatusNot(anyLong(), any())).willReturn(0L);
        given(documentRepository.existsByMemberIdAndOriginalFileNameAndStatusNot(anyLong(), anyString(), any())).willReturn(false);
        given(documentRepository.save(any(Document.class))).willAnswer(invocation -> {
            Document saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });
        given(documentValidator.getContentType("pdf")).willReturn("application/pdf");
        given(s3FileStorage.generatePresignedPutUrl(anyString(), anyString(), anyLong()))
                .willReturn(new S3FileStorage.PresignedUploadUrl("https://s3.example.com/presigned", Instant.now()));

        PresignResponse response = documentService.requestPresignedUrl(MEMBER_ID, request);

        assertThat(response.documentId()).isEqualTo(10L);
        assertThat(response.presignedUrl()).isNotNull();
    }

    @Test
    @DisplayName("INITIATED 문서를 confirm하면 UPLOADED로 전이된다")
    void confirmUploadSuccess() {
        Document document = createDocument(1L, DocumentStatus.INITIATED);
        given(documentRepository.findByIdAndMemberId(1L, MEMBER_ID)).willReturn(Optional.of(document));
        given(s3FileStorage.doesObjectExist(document.getStoragePath())).willReturn(true);

        DocumentResponse response = documentService.confirmUpload(MEMBER_ID, 1L);

        assertThat(response.status()).isEqualTo(DocumentStatus.UPLOADED);
        then(embeddingJobProducer).should().send(document);
    }

    @Test
    @DisplayName("문서 삭제 시 임베딩/S3/DB를 함께 정리한다")
    void deleteDocumentSuccess() {
        Document document = createDocument(1L, DocumentStatus.COMPLETED);
        given(documentRepository.findByIdAndMemberId(1L, MEMBER_ID)).willReturn(Optional.of(document));

        documentService.deleteDocument(MEMBER_ID, 1L);

        then(embeddingRepository).should().deleteByDocumentId(1L);
        then(s3FileStorage).should().deleteObject(document.getStoragePath());
        then(documentRepository).should().delete(document);
    }

    @Test
    @DisplayName("존재하지 않는 문서 삭제 시 DOCUMENT_NOT_FOUND 예외가 발생한다")
    void deleteDocumentNotFound() {
        given(documentRepository.findByIdAndMemberId(999L, MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.deleteDocument(MEMBER_ID, 999L))
                .isInstanceOf(PkvException.class)
                .satisfies(e -> assertThat(((PkvException) e).getErrorCode()).isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("문서 목록 조회")
    void getDocumentsSuccess() {
        Document document = createDocument(1L, DocumentStatus.COMPLETED);
        given(documentRepository.findByMemberIdAndStatusNotOrderByCreatedAtDesc(MEMBER_ID, DocumentStatus.INITIATED))
                .willReturn(List.of(document));

        List<DocumentResponse> response = documentService.getDocuments(MEMBER_ID);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(1L);
    }

    private Document createDocument(Long id, DocumentStatus status) {
        Document document = Document.builder()
                .memberId(MEMBER_ID)
                .originalFileName("test.pdf")
                .fileSize(1024L)
                .fileExtension("pdf")
                .status(status)
                .build();
        ReflectionTestUtils.setField(document, "id", id);
        document.assignStoragePath("documents/1234567890_test-uuid.pdf");
        return document;
    }
}
