package com.worktime.domain.model

/**
 * 근무 기록의 상태
 */
enum class WorkLogStatus {
    /**
     * 근로자가 출근했으며, 아직 근무 중
     */
    ONGOING,
    
    /**
     * 근로자가 퇴근했으며, 근무 완료
     */
    COMPLETED,
    
    /**
     * 근로자가 예정된 근무에 출근하지 않음
     */
    NO_SHOW,
    
    /**
     * 근무 기록이 고용주/관리자에 의해 수동으로 조정됨
     */
    MANUAL_ADJUSTED
}
