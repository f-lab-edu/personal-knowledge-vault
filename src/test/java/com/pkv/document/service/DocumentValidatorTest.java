package com.pkv.document.service;

import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentValidatorTest {

    private final DocumentValidator validator = new DocumentValidator();

    @Nested
    @DisplayName("validateDocumentName")
    class ValidateDocumentName {

        @Test
        @DisplayName("허용된 문자셋은 통과한다")
        void validName() {
            assertThatNoException().isThrownBy(() -> validator.validateDocumentName("설계서_v2-final"));
        }

        @Test
        @DisplayName("특수문자가 포함되면 실패한다")
        void specialCharacters() {
            assertThatThrownBy(() -> validator.validateDocumentName("file@name!"))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.DOCUMENT_NAME_INVALID));
        }
    }

    @Nested
    @DisplayName("validateExtension")
    class ValidateExtension {

        @Test
        @DisplayName("pdf/txt/md 확장자는 통과한다")
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
                    .satisfies(e -> assertErrorCode(e, ErrorCode.DOCUMENT_EXTENSION_NOT_SUPPORTED));
        }
    }

    @Nested
    @DisplayName("validateCountAndSize")
    class ValidateCountAndSize {

        @Test
        @DisplayName("문서 수/총량 한도 초과 시 실패한다")
        void exceeded() {
            assertThatThrownBy(() -> validator.validateDocumentCount(DocumentValidator.MAX_DOCUMENT_COUNT))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.DOCUMENT_COUNT_EXCEEDED));

            assertThatThrownBy(() -> validator.validateTotalSize(DocumentValidator.MAX_TOTAL_SIZE, 1))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertErrorCode(e, ErrorCode.DOCUMENT_TOTAL_SIZE_EXCEEDED));
        }
    }

    private static void assertErrorCode(Throwable e, ErrorCode expected) {
        org.assertj.core.api.Assertions.assertThat(((PkvException) e).getErrorCode()).isEqualTo(expected);
    }
}
