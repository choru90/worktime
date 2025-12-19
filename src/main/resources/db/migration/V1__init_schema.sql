-- V1__init_schema.sql
-- Initial database schema for worktime application

-- Users table (login accounts)
CREATE TABLE users (
    id BINARY(16) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY idx_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Owners table (business owners, 1:1 with users)
CREATE TABLE owners (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    business_name VARCHAR(100),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY idx_owner_user_id (user_id),
    CONSTRAINT fk_owner_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Workers table (part-time workers)
CREATE TABLE workers (
    id BINARY(16) NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    owner_id BINARY(16) NOT NULL,
    user_account_id BINARY(16),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_worker_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_worker_user_account FOREIGN KEY (user_account_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Wage contracts table
CREATE TABLE wage_contracts (
    id BINARY(16) NOT NULL,
    worker_id BINARY(16) NOT NULL,
    hourly_wage INT NOT NULL,
    effective_date DATE NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_wage_contract_worker FOREIGN KEY (worker_id) REFERENCES workers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Schedules table
CREATE TABLE schedules (
    id BINARY(16) NOT NULL,
    worker_id BINARY(16) NOT NULL,
    scheduled_date DATE NOT NULL,
    start_time TIME(6) NOT NULL,
    end_time TIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_worker FOREIGN KEY (worker_id) REFERENCES workers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Work logs table
CREATE TABLE work_logs (
    id BINARY(16) NOT NULL,
    worker_id BINARY(16) NOT NULL,
    schedule_id BINARY(16),
    clock_in_time TIMESTAMP(6) NOT NULL,
    clock_out_time TIMESTAMP(6),
    status VARCHAR(20) NOT NULL,
    memo VARCHAR(500),
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_work_log_worker FOREIGN KEY (worker_id) REFERENCES workers (id),
    CONSTRAINT fk_work_log_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
