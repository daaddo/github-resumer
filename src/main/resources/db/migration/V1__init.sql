CREATE TABLE users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    github_username   VARCHAR(255) NOT NULL,
    github_account_id BIGINT       NOT NULL,
    fcm_token         VARCHAR(512) NULL,
    avatar_url        VARCHAR(512) NULL,
    created_at        DATETIME(6)  NULL,
    updated_at        DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (github_username),
    CONSTRAINT uk_users_account_id UNIQUE (github_account_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE tracked_repositories (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    repo_full_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tracked_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_user_repo UNIQUE (user_id, repo_full_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_tracked_repo_full_name ON tracked_repositories (repo_full_name);
