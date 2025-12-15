package com.worktime.adapter.input.web.dto.dashboard

/**
 * 조치 필요 사항의 중요도
 */
enum class ActionSeverity {
    HIGH,   // 즉시 조치 필요 (예: 무단 결근, 장기 미퇴근)
    MEDIUM, // 주의 필요 (예: 지각)
    LOW     // 단순 확인 (예: 스케줄 승인 요청)
}
