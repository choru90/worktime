package com.worktime.adapter.input.web.dto.schedule

import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.*

/**
 * 스케줄 생성 요청 DTO
 */
data class ScheduleCreateRequest(
    @field:NotNull(message = "Worker ID is required")
    val workerId: UUID,
    
    @field:NotNull(message = "Start time is required")
    val startAtUtc: OffsetDateTime,
    
    @field:NotNull(message = "End time is required")
    val endAtUtc: OffsetDateTime,
    
    @field:NotNull(message = "Timezone is required")
    val timezone: String,
    
    @field:NotNull(message = "Request type is required")
    val requestType: ScheduleRequestType
) {
    init {
        require(endAtUtc.isAfter(startAtUtc)) {
            "End time must be after start time"
        }
    }
}

/**
 * 스케줄 생성 요청 타입
 */
enum class ScheduleRequestType {
    /**
     * 근로자가 요청
     */
    WORKER_REQUEST,
    
    /**
     * 고용주가 제안
     */
    OWNER_PROPOSAL
}
