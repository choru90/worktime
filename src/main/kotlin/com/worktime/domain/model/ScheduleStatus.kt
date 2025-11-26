package com.worktime.domain.model

/**
 * 근무 스케줄의 상태
 */
enum class ScheduleStatus {
    /**
     * 근로자가 요청한 스케줄
     */
    REQUESTED_BY_WORKER,
    
    /**
     * 고용주가 제안한 스케줄
     */
    PROPOSED_BY_OWNER,
    
    /**
     * 양측 모두 확정한 스케줄
     */
    CONFIRMED,
    
    /**
     * 거부된 스케줄
     */
    REJECTED,
    
    /**
     * 취소된 스케줄
     */
    CANCELED
}
