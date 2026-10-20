ALTER TABLE work_logs ADD COLUMN correction_count INT NOT NULL DEFAULT 0;
ALTER TABLE work_logs ADD COLUMN correction_reason VARCHAR(500) NULL;
ALTER TABLE work_logs ADD COLUMN rejection_reason VARCHAR(500) NULL;
CREATE TABLE work_log_corrections (
 id BINARY(16) NOT NULL PRIMARY KEY,
 work_log_id BINARY(16) NOT NULL,
 previous_in DATETIME(6) NOT NULL, previous_out DATETIME(6) NULL,
 previous_status VARCHAR(30) NOT NULL,
 requested_in DATETIME(6) NOT NULL, requested_out DATETIME(6) NOT NULL,
 previous_rejection_reason VARCHAR(500) NULL,
 reason VARCHAR(500) NOT NULL, revision INT NOT NULL, requested_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_correction_work_log FOREIGN KEY(work_log_id) REFERENCES work_logs(id),
 CONSTRAINT uq_work_log_revision UNIQUE(work_log_id,revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
