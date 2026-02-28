package com.pkv.worker.dto;

import java.util.List;

public record ChunkedDocument(
        List<Chunk> chunks
) {
    public record Chunk(
            String text,
            Long sourceId,
            Long memberId,
            String fileName,
            int pageNumber
    ) {}
}
