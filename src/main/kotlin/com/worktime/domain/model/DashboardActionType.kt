package com.worktime.domain.model

/**
 * 대시보드 예외/알림 타입
 * 
 * 대시보드에서 사용자에게 표시할 예외 상황이나 알림의 종류를 정의합니다.
 * 확장 가능하도록 설계되었으며, Sprint 1 이후 요구사항에 맞춰 추가될 수 있습니다.
 */
enum class DashboardActionType(val description: String) {
    
    // ========== WorkLog / Attendance 관련 ==========
    
    /**
     * 퇴근 미체크
     * 근로자가 출근은 했으나 퇴근 체크를 하지 않은 경우
     */
    MISSING_CHECKOUT("퇴근 미체크"),

    /**
     * 출근 미체크
     * 오늘 스케줄이 있는데 출근 체크인이 없음
     */
    MISSING_CHECKIN("출근 미체크"),
    
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
     * 승인 대기중 (Deprecated or Alias for PENDING_SCHEDULE_APPROVAL)
     */
    PENDING_APPROVAL("승인 대기중"),

    /**
     * 스케줄 승인 대기
     * 사장이 승인/거절해야 하는 스케줄
     */
    PENDING_SCHEDULE_APPROVAL("스케줄 승인 대기"),

    /**
     * 근무 임박
     */
    APPROACHING_SHIFT("근무 임박"),

    // ========== WageContract 관련 ==========

    /**
     * 근로 계약 만료 임박
     * 시급 계약이 N일 이내 만료 예정
     */
    WAGE_CONTRACT_EXPIRING_SOON("근로 계약 만료 임박");
}
