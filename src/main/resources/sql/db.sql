create table users
(
    id            INT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(50) UNIQUE NOT NULL,
    password      VARCHAR(255)       NOT NULL,
    full_name     VARCHAR(100)       NOT NULL,
    email         VARCHAR(100),
    phone         VARCHAR(20),
    status        ENUM ('ACTIVE','INACTIVE') DEFAULT 'ACTIVE',
    token_version VARCHAR(255),
    created_at    DATETIME                   DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME                   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at    DATETIME                   DEFAULT NULL
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
    user_id     BIGINT NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    revoked     BOOLEAN   DEFAULT FALSE,
    device_type VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revoked_at  TIMESTAMP,
    PRIMARY KEY (user_id,device_type)
);