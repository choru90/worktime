package com.worktime.adapter.input.web.dto.schedule

import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime

/**
 * 스케줄 수정 요청 DTO
 */
data class ScheduleUpdateRequest(
    @field:NotNull(message = "Start time is required")
    val startAtUtc: OffsetDateTime,
    
    @field:NotNull(message = "End time is required")
    val endAtUtc: OffsetDateTime,
    
    @field:NotNull(message = "Timezone is required")
    val timezone: String
) {
    init {
        require(endAtUtc.isAfter(startAtUtc)) {
            "End time must be after start time"
        }
    }
}
