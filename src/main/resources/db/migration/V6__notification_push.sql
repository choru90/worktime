CREATE TABLE push_devices (
    push_token VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BINARY(16) NOT NULL,
    binding_id BINARY(16) NOT NULL,
    PRIMARY KEY (push_token),
    INDEX idx_push_device_user (user_id),
    CONSTRAINT fk_push_device_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE push_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    push_token VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_id BINARY(16) NOT NULL,
    due_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    ticket_id VARCHAR(100),
    PRIMARY KEY (id),
    INDEX idx_push_due (due_at, id),
    INDEX idx_push_expiration (expires_at)
) ENGINE=InnoDB;
