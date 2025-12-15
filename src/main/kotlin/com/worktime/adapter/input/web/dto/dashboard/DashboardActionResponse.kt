package com.worktime.adapter.input.web.dto.dashboard

import com.worktime.domain.model.DashboardActionType
import java.time.OffsetDateTime
import java.util.*

/**
 * 대시보드 조치 필요 사항 응답 DTO
 */
data class DashboardActionResponse(
    /**
     * 원본 ID (스케줄 ID 또는 워크로그 ID)
     * 클라이언트에서 상세 화면으로 이동하거나 조치를 취할 때 사용
     */
    val id: UUID,
    
    /**
     * 조치 유형 (승인 대기, 미퇴근 등)
     */
    val type: DashboardActionType,
    
    /**
     * 제목 (예: "스케줄 승인 요청")
     */
    val title: String,
    
    /**
     * 상세 설명 (예: "홍길동님이 12월 25일 근무를 요청했습니다.")
     */
    val description: String,
    
    /**
     * 관련 근로자 이름
     */
    val workerName: String,
    
    /**
     * 발생 시간 (UTC)
     * 정렬 기준으로 사용됨
     */
    val createdAt: OffsetDateTime,
    
    /**
     * 중요도
     */
    val severity: ActionSeverity
)
