-- V2__add_store_and_shifts.sql
-- 영업장(Store), 영업시간(BusinessHours), 고정근무(RegularShift) 구조 추가

-- 1. Store (영업장) 테이블 생성
CREATE TABLE stores (
    id BINARY(16) NOT NULL,
    owner_id BINARY(16) NOT NULL,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255),
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_store_owner (owner_id),
    INDEX idx_store_active (active),
    CONSTRAINT fk_store_owner FOREIGN KEY (owner_id) REFERENCES owners (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. BusinessHours (영업시간) 테이블 생성
CREATE TABLE business_hours (
    id BINARY(16) NOT NULL,
    store_id BINARY(16) NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    open_time TIME NOT NULL,
    close_time TIME NOT NULL,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_business_hours_store (store_id),
    INDEX idx_business_hours_day (day_of_week),
    UNIQUE KEY uk_business_hours_store_day (store_id, day_of_week),
    CONSTRAINT fk_business_hours_store FOREIGN KEY (store_id) REFERENCES stores (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. RegularShift (고정근무) 테이블 생성
CREATE TABLE regular_shifts (
    id BINARY(16) NOT NULL,
    store_id BINARY(16) NOT NULL,
    worker_id BINARY(16) NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    shift_name VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from DATE,
    effective_until DATE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_regular_shift_store (store_id),
    INDEX idx_regular_shift_worker (worker_id),
    INDEX idx_regular_shift_day (day_of_week),
    INDEX idx_regular_shift_active (active),
    CONSTRAINT fk_regular_shift_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_regular_shift_worker FOREIGN KEY (worker_id) REFERENCES workers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Workers 테이블에 store_id 컬럼 추가
ALTER TABLE workers ADD COLUMN store_id BINARY(16);
ALTER TABLE workers ADD CONSTRAINT fk_worker_store FOREIGN KEY (store_id) REFERENCES stores (id);

-- 5. Schedules 테이블에 store_id, replacing_regular_shift_id 컬럼 추가
ALTER TABLE schedules ADD COLUMN store_id BINARY(16);
ALTER TABLE schedules ADD COLUMN replacing_regular_shift_id BINARY(16);
ALTER TABLE schedules ADD CONSTRAINT fk_schedule_store FOREIGN KEY (store_id) REFERENCES stores (id);
ALTER TABLE schedules ADD CONSTRAINT fk_schedule_regular_shift FOREIGN KEY (replacing_regular_shift_id) REFERENCES regular_shifts (id);

-- 6. WorkLogs 테이블에 regular_shift_id 컬럼 추가
ALTER TABLE work_logs ADD COLUMN regular_shift_id BINARY(16);
ALTER TABLE work_logs ADD CONSTRAINT fk_work_log_regular_shift FOREIGN KEY (regular_shift_id) REFERENCES regular_shifts (id);
