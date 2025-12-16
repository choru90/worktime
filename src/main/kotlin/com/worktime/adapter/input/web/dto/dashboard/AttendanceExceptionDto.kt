package com.worktime.adapter.input.web.dto.dashboard

import java.time.OffsetDateTime
import java.util.UUID

data class AttendanceExceptionDto(
    val type: AttendanceExceptionType,
    val scheduleId: UUID? = null,     // 스케줄 기반 예외일 경우 필수
    val workLogId: UUID? = null,      // 워크로그 기반 예외일 경우 필수
    val workerId: UUID,
    val workerName: String,
    
    // 시간 정보 (표시용)
    val scheduledStartAt: OffsetDateTime? = null,
    val scheduledEndAt: OffsetDateTime? = null,
    val checkedInAt: OffsetDateTime? = null,
    val checkedOutAt: OffsetDateTime? = null
)
