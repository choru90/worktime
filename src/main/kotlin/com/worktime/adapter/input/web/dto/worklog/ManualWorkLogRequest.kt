package com.worktime.adapter.input.web.dto.worklog

import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime

/**
 * 스케줄과 연결된 근무 기록 수동 입력 요청 DTO
 */
data class ManualWorkLogRequest(
    @field:NotNull(message = "출근 시간은 필수입니다")
    val clockInAt: OffsetDateTime,
    
    @field:NotNull(message = "퇴근 시간은 필수입니다")
    val clockOutAt: OffsetDateTime
) {
    init {
        require(clockOutAt.isAfter(clockInAt)) {
            "퇴근 시간은 출근 시간 이후여야 합니다"
        }
    }
}
