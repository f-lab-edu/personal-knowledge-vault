CREATE TABLE chat_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,

    member_id BIGINT NOT NULL,

    session_key VARCHAR(64) NOT NULL,

    title VARCHAR(255) NOT NULL,

    question_count INT NOT NULL DEFAULT 0,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE INDEX uk_chat_sessions_member_session_key (member_id, session_key),
    CONSTRAINT fk_chat_sessions_member FOREIGN KEY (member_id) REFERENCES users(id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chat_histories (
    id BIGINT NOT NULL AUTO_INCREMENT,

    member_id BIGINT NOT NULL,

    session_id BIGINT NOT NULL,

    question TEXT NOT NULL,

    answer TEXT NULL,

    status VARCHAR(20) NOT NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_chat_histories_member FOREIGN KEY (member_id) REFERENCES users(id),
    CONSTRAINT fk_chat_histories_session FOREIGN KEY (session_id) REFERENCES chat_sessions(id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chat_history_sources (
    id BIGINT NOT NULL AUTO_INCREMENT,

    history_id BIGINT NOT NULL,

    source_id BIGINT NULL,

    source_file_name VARCHAR(100) NOT NULL,

    source_page_number INT NULL,

    snippet VARCHAR(200) NOT NULL,

    source_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    display_order INT NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_chat_history_sources_history FOREIGN KEY (history_id) REFERENCES chat_histories(id),
    CONSTRAINT fk_chat_history_sources_source FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE SET NULL

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
