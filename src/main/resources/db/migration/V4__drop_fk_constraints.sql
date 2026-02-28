ALTER TABLE sources
DROP FOREIGN KEY fk_sources_member;

ALTER TABLE chat_sessions
DROP FOREIGN KEY fk_chat_sessions_member;

ALTER TABLE chat_histories
DROP FOREIGN KEY fk_chat_histories_member;

ALTER TABLE chat_histories
DROP FOREIGN KEY fk_chat_histories_session;

ALTER TABLE chat_history_sources
DROP FOREIGN KEY fk_chat_history_sources_history;

ALTER TABLE chat_history_sources
DROP FOREIGN KEY fk_chat_history_sources_source;
