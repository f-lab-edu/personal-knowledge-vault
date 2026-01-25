CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,

    google_id VARCHAR(255) NOT NULL,

    email VARCHAR(255) NOT NULL,

    name VARCHAR(100) NOT NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    deleted_at DATETIME(6) NULL,

    PRIMARY KEY (id),
    UNIQUE INDEX uk_users_google_id (google_id),
    UNIQUE INDEX uk_users_email (email)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
