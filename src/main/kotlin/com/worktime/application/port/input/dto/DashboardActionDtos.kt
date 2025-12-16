package com.worktime.application.port.input.dto

import com.worktime.domain.model.DashboardActionType
import java.time.OffsetDateTime

/**
 * 대시보드 Action Center API 응답
 */
data class DashboardActionListResponse(
    val date: String, // YYYY-MM-DD
    val timezone: String, // e.g., Asia/Seoul
    val totalCount: Int,
    val items: List<ActionItem>
)

/**
 * 개별 Action 항목
 */
data class ActionItem(
    val id: String, // action:type:subtype:uuid
    val type: DashboardActionType,
    val priority: Int,
    val title: String,
    val message: String,
    val createdAt: OffsetDateTime,
    val target: ActionTarget,
    val meta: ActionMeta
)

/**
 * 클릭 시 이동할 타겟 정보
 */
data class ActionTarget(
    val targetType: ActionTargetType, // SCHEDULE, ATTENDANCE, WORKER
    val targetId: String,
    val deepLink: String
)

enum class ActionTargetType {
    SCHEDULE,
    ATTENDANCE,
    WORKER,
    WAGE_CONTRACT
}

/**
 * 추가 메타데이터 (프론트엔드 표시용)
 */
data class ActionMeta(
    val workerId: String,
    val workerName: String,
    
    // Schedule related
    val scheduledStartAt: OffsetDateTime? = null,
    val scheduledEndAt: OffsetDateTime? = null,
    
    // WorkLog related
    val checkedInAt: OffsetDateTime? = null,
    val checkedOutAt: OffsetDateTime? = null,
    
    // Contract related
    val contractEndDate: String? = null // YYYY-MM-DD
)
