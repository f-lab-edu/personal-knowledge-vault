package com.pkv.worker.dto;

import java.util.List;

public record ParsedDocument(
        String fullText,
        List<PageOffset> pageOffsets
) {
    public record PageOffset(int pageNumber, int startOffset) {}
}
