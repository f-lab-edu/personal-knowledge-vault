CREATE TABLE chat_threads (
    id BIGINT NOT NULL AUTO_INCREMENT,

    member_id BIGINT NOT NULL,

    thread_key VARCHAR(64) NOT NULL,

    title VARCHAR(255) NOT NULL,

    turn_count INT NOT NULL DEFAULT 0,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE INDEX uk_chat_threads_member_thread_key (member_id, thread_key),
    CONSTRAINT fk_chat_threads_member FOREIGN KEY (member_id) REFERENCES users(id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE thread_turns (
    id BIGINT NOT NULL AUTO_INCREMENT,

    member_id BIGINT NOT NULL,

    thread_id BIGINT NOT NULL,

    prompt TEXT NOT NULL,

    answer TEXT NULL,

    status VARCHAR(20) NOT NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_thread_turns_member FOREIGN KEY (member_id) REFERENCES users(id),
    CONSTRAINT fk_thread_turns_thread FOREIGN KEY (thread_id) REFERENCES chat_threads(id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE turn_citations (
    id BIGINT NOT NULL AUTO_INCREMENT,

    turn_id BIGINT NOT NULL,

    document_id BIGINT NULL,

    document_file_name VARCHAR(100) NOT NULL,

    document_page_number INT NULL,

    snippet VARCHAR(200) NOT NULL,

    display_order INT NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_turn_citations_turn FOREIGN KEY (turn_id) REFERENCES thread_turns(id) ON DELETE CASCADE,
    CONSTRAINT fk_turn_citations_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE SET NULL

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
