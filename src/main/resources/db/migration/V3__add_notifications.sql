-- V3__add_notifications.sql
-- 알림(Notification) 테이블 생성

CREATE TABLE notifications (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    title VARCHAR(100) NOT NULL,
    message VARCHAR(500) NOT NULL,
    type VARCHAR(30) NOT NULL,
    reference_id BINARY(16),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_notification_user ON notifications (user_id);
CREATE INDEX idx_notification_created ON notifications (created_at);
CREATE INDEX idx_notification_read ON notifications (is_read);
