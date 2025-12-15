create table user
(
    id         INT PRIMARY KEY AUTO_INCREMENT,
    username   VARCHAR(50) UNIQUE NOT NULL,
    password   VARCHAR(255)       NOT NULL,
    full_name  VARCHAR(100)       NOT NULL,
    email      VARCHAR(100),
    phone      VARCHAR(20),
    status     ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME DEFAULT NULL
)


create table user_role
(
    user_id INT         NOT NULL,
    role    VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role),
    FOREIGN KEY (user_id) REFERENCES user (id)
)