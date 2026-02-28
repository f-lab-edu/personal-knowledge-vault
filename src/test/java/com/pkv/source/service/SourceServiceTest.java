package com.pkv.source.service;

import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import com.pkv.common.service.EmbeddingRepository;
import com.pkv.chat.repository.ChatHistorySourceRepository;
import com.pkv.member.repository.MemberRepository;
import com.pkv.source.domain.Source;
import com.pkv.source.domain.SourceStatus;
import com.pkv.source.dto.PresignRequest;
import com.pkv.source.dto.PresignResponse;
import com.pkv.source.dto.SourceResponse;
import com.pkv.source.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private SourceValidator sourceValidator;

    @Mock
    private S3FileStorage s3FileStorage;

    @Mock
    private EmbeddingJobProducer embeddingJobProducer;

    @Mock
    private EmbeddingRepository embeddingRepository;

    @Mock
    private ChatHistorySourceRepository chatHistorySourceRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private SourceService sourceService;

    private static final Long MEMBER_ID = 1L;

    @BeforeEach
    void setUp() {
        lenient().when(memberRepository.existsByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(true);
    }

    private Source createSource(Long id, SourceStatus status) {
        Source source = Source.builder()
                .memberId(MEMBER_ID)
                .originalFileName("test.pdf")
                .fileSize(1024L)
                .fileExtension("pdf")
                .status(status)
                .build();
        ReflectionTestUtils.setField(source, "id", id);
        source.assignStoragePath("sources/1234567890_test-uuid.pdf");
        return source;
    }

    @Nested
    @DisplayName("requestPresignedUrl")
    class RequestPresignedUrl {

        @Test
        @DisplayName("유효한 파일 정보로 요청하면 Presigned URL과 sourceId를 반환한다")
        void success() {
            // given
            PresignRequest request = new PresignRequest("설계서.pdf", 1024L);

            given(sourceRepository.countByMemberIdAndStatusNot(eq(MEMBER_ID), any()))
                    .willReturn(0L);
            given(sourceRepository.sumFileSizeByMemberIdAndStatusNot(eq(MEMBER_ID), any()))
                    .willReturn(0L);
            given(sourceRepository.existsByMemberIdAndOriginalFileNameAndStatusNot(
                    eq(MEMBER_ID), anyString(), any()))
                    .willReturn(false);

            given(sourceRepository.save(any(Source.class))).willAnswer(invocation -> {
                Source saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", 10L);
                return saved;
            });

            given(sourceValidator.getContentType("pdf")).willReturn("application/pdf");
            given(s3FileStorage.generatePresignedPutUrl(anyString(), anyString(), anyLong()))
                    .willReturn(new S3FileStorage.PresignedUploadUrl(
                            "https://s3.example.com/presigned", Instant.now()));

            // when
            PresignResponse response = sourceService.requestPresignedUrl(MEMBER_ID, request);

            // then
            assertThat(response.sourceId()).isEqualTo(10L);
            assertThat(response.presignedUrl()).isNotNull();
        }

        @Test
        @DisplayName("지원하지 않는 확장자(jpg)이면 SOURCE_EXTENSION_NOT_SUPPORTED 예외가 발생한다")
        void invalidExtension() {
            // given
            PresignRequest request = new PresignRequest("photo.jpg", 1024L);

            org.mockito.BDDMockito
                    .willThrow(new PkvException(ErrorCode.SOURCE_EXTENSION_NOT_SUPPORTED))
                    .given(sourceValidator).validateExtension("jpg");

            // when & then
            assertThatThrownBy(() -> sourceService.requestPresignedUrl(MEMBER_ID, request))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_EXTENSION_NOT_SUPPORTED));
        }

        @Test
        @DisplayName("동일 파일명의 INITIATED 레코드가 있으면 삭제 후 새로 생성한다")
        void cleansUpInitiated() {
            // given
            PresignRequest request = new PresignRequest("설계서.pdf", 1024L);

            given(sourceRepository.countByMemberIdAndStatusNot(eq(MEMBER_ID), any()))
                    .willReturn(0L);
            given(sourceRepository.sumFileSizeByMemberIdAndStatusNot(eq(MEMBER_ID), any()))
                    .willReturn(0L);
            given(sourceRepository.existsByMemberIdAndOriginalFileNameAndStatusNot(
                    eq(MEMBER_ID), anyString(), any()))
                    .willReturn(false);

            given(sourceRepository.save(any(Source.class))).willAnswer(invocation -> {
                Source saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", 10L);
                return saved;
            });

            given(sourceValidator.getContentType("pdf")).willReturn("application/pdf");
            given(s3FileStorage.generatePresignedPutUrl(anyString(), anyString(), anyLong()))
                    .willReturn(new S3FileStorage.PresignedUploadUrl(
                            "https://s3.example.com/presigned", Instant.now()));

            // when
            sourceService.requestPresignedUrl(MEMBER_ID, request);

            // then
            then(sourceRepository).should().deleteByMemberIdAndOriginalFileNameAndStatus(
                    MEMBER_ID, "설계서.pdf", SourceStatus.INITIATED);
        }

        @Test
        @DisplayName("삭제된 회원 또는 존재하지 않는 회원이면 MEMBER_NOT_FOUND 예외가 발생한다")
        void memberNotFound() {
            // given
            PresignRequest request = new PresignRequest("설계서.pdf", 1024L);
            given(memberRepository.existsByIdAndDeletedAtIsNull(MEMBER_ID)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> sourceService.requestPresignedUrl(MEMBER_ID, request))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.MEMBER_NOT_FOUND));

            then(sourceRepository).should(never()).save(any(Source.class));
        }
    }

    @Nested
    @DisplayName("confirmUpload")
    class ConfirmUpload {

        @Test
        @DisplayName("INITIATED 상태의 소스를 확인하면 UPLOADED 상태로 변경된다")
        void success() {
            // given
            Source source = createSource(1L, SourceStatus.INITIATED);

            given(sourceRepository.findByIdAndMemberId(1L, MEMBER_ID))
                    .willReturn(Optional.of(source));
            given(s3FileStorage.doesObjectExist(source.getStoragePath()))
                    .willReturn(true);

            // when
            SourceResponse response = sourceService.confirmUpload(MEMBER_ID, 1L);

            // then
            assertThat(response.status()).isEqualTo(SourceStatus.UPLOADED);
            then(embeddingJobProducer).should().send(source); // confirmUpload 시 Kafka 메시지 발행이 호출되는지 검증
        }

        @Test
        @DisplayName("존재하지 않는 sourceId로 요청하면 SOURCE_NOT_FOUND 예외가 발생한다")
        void sourceNotFound() {
            // given
            given(sourceRepository.findByIdAndMemberId(999L, MEMBER_ID))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> sourceService.confirmUpload(MEMBER_ID, 999L))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_NOT_FOUND));
        }

        @Test
        @DisplayName("INITIATED가 아닌 상태(UPLOADED)에서 confirm하면 SOURCE_UPLOAD_NOT_CONFIRMED 예외가 발생한다")
        void notInitiatedStatus() {
            // given
            Source source = createSource(1L, SourceStatus.UPLOADED);

            given(sourceRepository.findByIdAndMemberId(1L, MEMBER_ID))
                    .willReturn(Optional.of(source));

            // when & then
            assertThatThrownBy(() -> sourceService.confirmUpload(MEMBER_ID, 1L))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_UPLOAD_NOT_CONFIRMED));
        }

        @Test
        @DisplayName("S3에 파일이 존재하지 않으면 SOURCE_UPLOAD_NOT_CONFIRMED 예외가 발생한다")
        void s3FileNotFound() {
            // given
            Source source = createSource(1L, SourceStatus.INITIATED);

            given(sourceRepository.findByIdAndMemberId(1L, MEMBER_ID))
                    .willReturn(Optional.of(source));
            given(s3FileStorage.doesObjectExist(source.getStoragePath()))
                    .willReturn(false);

            // when & then
            assertThatThrownBy(() -> sourceService.confirmUpload(MEMBER_ID, 1L))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_UPLOAD_NOT_CONFIRMED));
        }

        @Test
        @DisplayName("삭제된 회원 또는 존재하지 않는 회원이면 MEMBER_NOT_FOUND 예외가 발생한다")
        void memberNotFound() {
            // given
            given(memberRepository.existsByIdAndDeletedAtIsNull(MEMBER_ID)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> sourceService.confirmUpload(MEMBER_ID, 1L))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.MEMBER_NOT_FOUND));

            then(sourceRepository).should(never()).findByIdAndMemberId(anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("deleteSource")
    class DeleteSource {

        @Test
        @DisplayName("COMPLETED 상태의 소스를 삭제하면 벡터, S3 파일, DB 레코드가 모두 삭제된다")
        void deleteCompleted() {
            // given
            Source source = createSource(1L, SourceStatus.COMPLETED);

            given(sourceRepository.findByIdAndMemberId(1L, MEMBER_ID))
                    .willReturn(Optional.of(source));

            // when
            sourceService.deleteSource(MEMBER_ID, 1L);

            // then
            then(chatHistorySourceRepository).should().clearSourceIdBySourceId(1L);
            then(embeddingRepository).should().deleteBySourceId(1L);
            then(s3FileStorage).should().deleteObject(source.getStoragePath());
            then(sourceRepository).should().delete(source);
        }

        @Test
        @DisplayName("FAILED 상태의 소스를 삭제하면 벡터, S3 파일, DB 레코드가 모두 삭제된다")
        void deleteFailed() {
            // given
            Source source = createSource(1L, SourceStatus.FAILED);

            given(sourceRepository.findByIdAndMemberId(1L, MEMBER_ID))
                    .willReturn(Optional.of(source));

            // when
            sourceService.deleteSource(MEMBER_ID, 1L);

            // then
            then(chatHistorySourceRepository).should().clearSourceIdBySourceId(1L);
            then(embeddingRepository).should().deleteBySourceId(1L);
            then(s3FileStorage).should().deleteObject(source.getStoragePath());
            then(sourceRepository).should().delete(source);
        }

        @Test
        @DisplayName("UPLOADED 상태의 소스는 삭제할 수 없어 SOURCE_DELETE_NOT_ALLOWED 예외가 발생한다")
        void cannotDeleteUploaded() {
            // given
            Source source = createSource(1L, SourceStatus.UPLOADED);

            given(sourceRepository.findByIdAndMemberId(1L, MEMBER_ID))
                    .willReturn(Optional.of(source));

            // when & then
            assertThatThrownBy(() -> sourceService.deleteSource(MEMBER_ID, 1L))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_DELETE_NOT_ALLOWED));

            then(chatHistorySourceRepository).should(never()).clearSourceIdBySourceId(anyLong());
            then(embeddingRepository).should(never()).deleteBySourceId(anyLong());
        }

        @Test
        @DisplayName("PROCESSING 상태의 소스는 삭제할 수 없어 SOURCE_DELETE_NOT_ALLOWED 예외가 발생한다")
        void cannotDeleteProcessing() {
            // given
            Source source = createSource(1L, SourceStatus.PROCESSING);

            given(sourceRepository.findByIdAndMemberId(1L, MEMBER_ID))
                    .willReturn(Optional.of(source));

            // when & then
            assertThatThrownBy(() -> sourceService.deleteSource(MEMBER_ID, 1L))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_DELETE_NOT_ALLOWED));

            then(chatHistorySourceRepository).should(never()).clearSourceIdBySourceId(anyLong());
            then(embeddingRepository).should(never()).deleteBySourceId(anyLong());
        }

        @Test
        @DisplayName("존재하지 않는 sourceId로 삭제하면 SOURCE_NOT_FOUND 예외가 발생한다")
        void sourceNotFound() {
            // given
            given(sourceRepository.findByIdAndMemberId(999L, MEMBER_ID))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> sourceService.deleteSource(MEMBER_ID, 999L))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_NOT_FOUND));

            then(chatHistorySourceRepository).should(never()).clearSourceIdBySourceId(anyLong());
            then(embeddingRepository).should(never()).deleteBySourceId(anyLong());
        }

        @Test
        @DisplayName("삭제된 회원 또는 존재하지 않는 회원이면 MEMBER_NOT_FOUND 예외가 발생한다")
        void memberNotFound() {
            // given
            given(memberRepository.existsByIdAndDeletedAtIsNull(MEMBER_ID)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> sourceService.deleteSource(MEMBER_ID, 1L))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.MEMBER_NOT_FOUND));

            then(sourceRepository).should(never()).findByIdAndMemberId(anyLong(), anyLong());
            then(chatHistorySourceRepository).should(never()).clearSourceIdBySourceId(anyLong());
            then(embeddingRepository).should(never()).deleteBySourceId(anyLong());
        }
    }

    @Nested
    @DisplayName("getSources")
    class GetSources {

        @Test
        @DisplayName("INITIATED 상태를 제외한 소스 목록을 최신순으로 반환한다")
        void success() {
            // given
            Source source = createSource(1L, SourceStatus.COMPLETED);

            given(sourceRepository.findByMemberIdAndStatusNotOrderByCreatedAtDesc(
                    MEMBER_ID, SourceStatus.INITIATED))
                    .willReturn(List.of(source));

            // when
            List<SourceResponse> result = sourceService.getSources(MEMBER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(1L);
        }
    }

    private static void assertErrorCode(Throwable e, ErrorCode expected) {
        assertThat(((PkvException) e).getErrorCode()).isEqualTo(expected);
    }
}
