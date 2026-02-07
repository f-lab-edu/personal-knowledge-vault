package com.pkv.source.service;

import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class SourceValidator {

    static final long MAX_FILE_SIZE = 31_457_280L;
    static final int MAX_SOURCE_COUNT = 30;
    static final long MAX_TOTAL_SIZE = 314_572_800L;
    static final Map<String, String> ALLOWED_EXTENSIONS = Map.of(
            "pdf", "application/pdf",
            "txt", "text/plain",
            "md", "text/markdown"
    );
    static final Pattern SOURCE_NAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9_-]{1,30}$");

    public void validateSourceName(String sourceName) {
        if (!SOURCE_NAME_PATTERN.matcher(Normalizer.normalize(sourceName, Normalizer.Form.NFC)).matches()) {
            throw new PkvException(ErrorCode.SOURCE_NAME_INVALID);
        }
    }

    public void validateExtension(String extension) {
        if (!ALLOWED_EXTENSIONS.containsKey(extension)) {
            throw new PkvException(ErrorCode.SOURCE_EXTENSION_NOT_SUPPORTED);
        }
    }

    public String getContentType(String extension) {
        return ALLOWED_EXTENSIONS.getOrDefault(extension, "application/octet-stream");
    }

    public void validateFileSize(long fileSize) {
        if (fileSize > MAX_FILE_SIZE) {
            throw new PkvException(ErrorCode.SOURCE_SIZE_EXCEEDED);
        }
    }

    public void validateSourceCount(long currentCount) {
        if (currentCount >= MAX_SOURCE_COUNT) {
            throw new PkvException(ErrorCode.SOURCE_COUNT_EXCEEDED);
        }
    }

    public void validateTotalSize(long currentTotal, long newFileSize) {
        if (currentTotal + newFileSize > MAX_TOTAL_SIZE) {
            throw new PkvException(ErrorCode.SOURCE_TOTAL_SIZE_EXCEEDED);
        }
    }

    public void validateDuplicateSourceName(boolean exists) {
        if (exists) {
            throw new PkvException(ErrorCode.SOURCE_NAME_DUPLICATED);
        }
    }
}
