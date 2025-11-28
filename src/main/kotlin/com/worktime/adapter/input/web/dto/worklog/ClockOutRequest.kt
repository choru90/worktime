package com.worktime.adapter.input.web.dto.worklog

import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime

/**
 * 퇴근 요청 DTO
 */
data class ClockOutRequest(
    @field:NotNull(message = "Clock-out time is required")
    val clockOutAt: OffsetDateTime
)
