CREATE TABLE sources (
    id BIGINT NOT NULL AUTO_INCREMENT,

    member_id BIGINT NOT NULL,

    original_file_name VARCHAR(100) NOT NULL,

    storage_path VARCHAR(500) NULL,

    file_size BIGINT NOT NULL,

    file_extension VARCHAR(10) NOT NULL,

    status VARCHAR(20) NOT NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_sources_member FOREIGN KEY (member_id) REFERENCES users(id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
