package com.worktime.domain.model

/**
 * 대시보드 예외/알림 타입
 * 
 * 대시보드에서 사용자에게 표시할 예외 상황이나 알림의 종류를 정의합니다.
 * 확장 가능하도록 설계되었으며, Sprint 1 이후 요구사항에 맞춰 추가될 수 있습니다.
 */
enum class DashboardActionType(val description: String) {
    
    // ========== WorkLog 관련 ==========
    
    /**
     * 퇴근 미체크
     * 근로자가 출근은 했으나 퇴근 체크를 하지 않은 경우
     */
    MISSING_CHECKOUT("퇴근 미체크"),
    
    /**
     * 지각
     * 예정된 근무 시작 시간보다 늦게 출근한 경우
     */
    LATE_ARRIVAL("지각"),
    
    /**
     * 조퇴
     * 예정된 근무 종료 시간보다 일찍 퇴근한 경우
     */
    EARLY_DEPARTURE("조퇴"),
    
    // ========== Schedule 관련 ==========
    
    /**
     * 승인 대기중
     * 근로자 또는 고용주가 제안한 스케줄이 상대방의 승인을 기다리는 상태
     */
    PENDING_APPROVAL("승인 대기중"),
    
    /**
     * 근무 임박
     * 예정된 근무 시작 시간이 임박한 경우 (예: 1시간 전)
     */
    APPROACHING_SHIFT("근무 임박");
    
    // TODO: Sprint 1 이후 추가 예정
    // - OVERTIME: 초과 근무
    // - MISSING_CHECKIN: 출근 미체크
    // - SCHEDULE_CONFLICT: 스케줄 충돌
    // - WAGE_DISPUTE: 급여 분쟁
}
