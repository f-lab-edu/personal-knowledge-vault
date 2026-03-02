package com.pkv.document.service;

import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class DocumentValidator {

    static final long MAX_FILE_SIZE = 31_457_280L;
    static final int MAX_DOCUMENT_COUNT = 30;
    static final long MAX_TOTAL_SIZE = 314_572_800L;
    static final Map<String, String> ALLOWED_EXTENSIONS = Map.of(
            "pdf", "application/pdf",
            "txt", "text/plain",
            "md", "text/markdown"
    );
    static final Pattern DOCUMENT_NAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9_-]{1,30}$");

    public void validateDocumentName(String documentName) {
        if (!DOCUMENT_NAME_PATTERN.matcher(Normalizer.normalize(documentName, Normalizer.Form.NFC)).matches()) {
            throw new PkvException(ErrorCode.DOCUMENT_NAME_INVALID);
        }
    }

    public void validateExtension(String extension) {
        if (!ALLOWED_EXTENSIONS.containsKey(extension)) {
            throw new PkvException(ErrorCode.DOCUMENT_EXTENSION_NOT_SUPPORTED);
        }
    }

    public String getContentType(String extension) {
        return ALLOWED_EXTENSIONS.getOrDefault(extension, "application/octet-stream");
    }

    public void validateFileSize(long fileSize) {
        if (fileSize > MAX_FILE_SIZE) {
            throw new PkvException(ErrorCode.DOCUMENT_SIZE_EXCEEDED);
        }
    }

    public void validateDocumentCount(long currentCount) {
        if (currentCount >= MAX_DOCUMENT_COUNT) {
            throw new PkvException(ErrorCode.DOCUMENT_COUNT_EXCEEDED);
        }
    }

    public void validateTotalSize(long currentTotal, long newFileSize) {
        if (currentTotal + newFileSize > MAX_TOTAL_SIZE) {
            throw new PkvException(ErrorCode.DOCUMENT_TOTAL_SIZE_EXCEEDED);
        }
    }

    public void validateDuplicateDocumentName(boolean exists) {
        if (exists) {
            throw new PkvException(ErrorCode.DOCUMENT_NAME_DUPLICATED);
        }
    }
}
