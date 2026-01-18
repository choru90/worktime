package com.worktime.domain.model

/**
 * 알림 타입
 */
enum class NotificationType {
    /**
     * 사장이 알바에게 스케줄을 제안했을 때
     */
    SCHEDULE_PROPOSED,
    
    /**
     * 스케줄이 확정되었을 때
     */
    SCHEDULE_CONFIRMED,
    
    /**
     * 스케줄이 거절되었을 때
     */
    SCHEDULE_REJECTED,
    
    /**
     * 스케줄이 취소되었을 때
     */
    SCHEDULE_CANCELED
}
