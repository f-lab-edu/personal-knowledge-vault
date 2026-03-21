ALTER TABLE documents
DROP FOREIGN KEY fk_documents_member;

ALTER TABLE chat_threads
DROP FOREIGN KEY fk_chat_threads_member;

ALTER TABLE thread_turns
DROP FOREIGN KEY fk_thread_turns_member;

ALTER TABLE thread_turns
DROP FOREIGN KEY fk_thread_turns_thread;

ALTER TABLE turn_citations
DROP FOREIGN KEY fk_turn_citations_turn;

ALTER TABLE turn_citations
DROP FOREIGN KEY fk_turn_citations_document;
