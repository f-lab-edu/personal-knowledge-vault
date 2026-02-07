package com.pkv.source.service;

import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceValidatorTest {

    private final SourceValidator validator = new SourceValidator();

    @Nested
    @DisplayName("validateSourceName")
    class ValidateSourceName {

        @Test
        @DisplayName("한글, 영문, 숫자, _, -로 구성된 이름은 통과한다")
        void validName() {
            assertThatNoException().isThrownBy(() -> validator.validateSourceName("설계서_v2-final"));
        }

        @Test
        @DisplayName("특수문자가 포함되면 실패한다")
        void specialCharacters() {
            assertThatThrownBy(() -> validator.validateSourceName("file@name!"))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_NAME_INVALID));
        }

        @Test
        @DisplayName("31자 이상이면 실패한다")
        void tooLong() {
            assertThatThrownBy(() -> validator.validateSourceName("a".repeat(31)))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_NAME_INVALID));
        }

        @Test
        @DisplayName("빈 문자열이면 실패한다")
        void empty() {
            assertThatThrownBy(() -> validator.validateSourceName(""))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_NAME_INVALID));
        }

        @Test
        @DisplayName("띄어쓰기가 포함되면 실패한다")
        void containsSpace() {
            assertThatThrownBy(() -> validator.validateSourceName("my file"))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_NAME_INVALID));
        }
    }

    @Nested
    @DisplayName("validateExtension")
    class ValidateExtension {

        @Test
        @DisplayName("pdf, txt, md 확장자는 통과한다")
        void allowedExtensions() {
            assertThatNoException().isThrownBy(() -> validator.validateExtension("pdf"));
            assertThatNoException().isThrownBy(() -> validator.validateExtension("txt"));
            assertThatNoException().isThrownBy(() -> validator.validateExtension("md"));
        }

        @Test
        @DisplayName("지원하지 않는 확장자는 실패한다")
        void unsupportedExtension() {
            assertThatThrownBy(() -> validator.validateExtension("doc"))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_EXTENSION_NOT_SUPPORTED));

            assertThatThrownBy(() -> validator.validateExtension("jpg"))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_EXTENSION_NOT_SUPPORTED));
        }
    }

    @Nested
    @DisplayName("validateFileSize")
    class ValidateFileSize {

        @Test
        @DisplayName("30MB 이하는 통과한다")
        void withinLimit() {
            assertThatNoException().isThrownBy(() -> validator.validateFileSize(SourceValidator.MAX_FILE_SIZE));
        }

        @Test
        @DisplayName("30MB 초과는 실패한다")
        void exceeded() {
            assertThatThrownBy(() -> validator.validateFileSize(SourceValidator.MAX_FILE_SIZE + 1))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_SIZE_EXCEEDED));
        }
    }

    @Nested
    @DisplayName("validateSourceCount")
    class ValidateSourceCount {

        @Test
        @DisplayName("29개는 통과한다")
        void withinLimit() {
            assertThatNoException().isThrownBy(() -> validator.validateSourceCount(29));
        }

        @Test
        @DisplayName("30개는 실패한다")
        void exceeded() {
            assertThatThrownBy(() -> validator.validateSourceCount(30))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_COUNT_EXCEEDED));
        }
    }

    @Nested
    @DisplayName("validateTotalSize")
    class ValidateTotalSize {

        @Test
        @DisplayName("합산 300MB 이하는 통과한다")
        void withinLimit() {
            assertThatNoException().isThrownBy(
                    () -> validator.validateTotalSize(SourceValidator.MAX_TOTAL_SIZE - 100, 100));
        }

        @Test
        @DisplayName("합산 300MB 초과는 실패한다")
        void exceeded() {
            assertThatThrownBy(() -> validator.validateTotalSize(SourceValidator.MAX_TOTAL_SIZE, 1))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.SOURCE_TOTAL_SIZE_EXCEEDED));
        }
    }

    private static void assertErrorCode(Throwable e, ErrorCode expected) {
        org.assertj.core.api.Assertions.assertThat(((PkvException) e).getErrorCode()).isEqualTo(expected);
    }
}
