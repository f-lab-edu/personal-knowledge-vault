ALTER TABLE chat_history_sources
ADD COLUMN source_chunk_ref VARCHAR(64) NULL AFTER source_page_number;
