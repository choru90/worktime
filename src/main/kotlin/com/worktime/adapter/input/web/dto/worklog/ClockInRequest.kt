package com.worktime.adapter.input.web.dto.worklog

import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.*

/**
 * 출근 요청 DTO
 */
data class ClockInRequest(
    /**
     * 연결할 스케줄 ID (선택사항)
     */
    val scheduleId: UUID? = null,
    
    @field:NotNull(message = "Clock-in time is required")
    val clockInAt: OffsetDateTime
)
