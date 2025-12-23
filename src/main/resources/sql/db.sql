create table users
(
    id         INT PRIMARY KEY AUTO_INCREMENT,
    username   VARCHAR(50) UNIQUE NOT NULL,
    password   VARCHAR(255)       NOT NULL,
    full_name  VARCHAR(100)       NOT NULL,
    email      VARCHAR(100),
    phone      VARCHAR(20),
    status     ENUM ('ACTIVE','INACTIVE') DEFAULT 'ACTIVE',
    created_at DATETIME                   DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME                   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME                   DEFAULT NULL
);


create table user_role
(
    user_id INT         NOT NULL,
    role    VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE refresh_tokens
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     INT          NOT NULL,
    device_type VARCHAR(20)  NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    revoked     BOOLEAN   DEFAULT FALSE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revoked_at  TIMESTAMP
);

CREATE INDEX idx_refresh_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_user_device_revoked ON refresh_tokens (user_id, device_type, revoked);

CREATE TABLE user_sessions
(
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       INT         NOT NULL,
    device_type   VARCHAR(10) NOT NULL,
    token_version BIGINT      NOT NULL DEFAULT 1,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login    TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
    created_at    TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_service (user_id, device_type)
)