package com.worktime.adapter.input.web.dto.worklog
import com.worktime.domain.model.WorkLogStatus
import jakarta.validation.constraints.*
import java.time.OffsetDateTime
data class CorrectWorkLogRequest(
 val clockInAt: OffsetDateTime, val clockOutAt: OffsetDateTime,
 @field:NotBlank @field:Size(max=500) val reason: String,
 @field:Min(0) val expectedCorrectionCount: Int, val expectedStatus: WorkLogStatus
)
