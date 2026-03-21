ALTER TABLE turn_citations
ADD COLUMN source_chunk_ref VARCHAR(64) NULL AFTER document_page_number;
