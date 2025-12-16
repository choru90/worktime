package com.worktime.adapter.input.web.dto.dashboard

import java.time.LocalDate

data class AttendanceSummaryResponse(
    val date: LocalDate,
    val timezone: String = "Asia/Seoul",
    val summary: AttendanceCounts,
    val exceptions: List<AttendanceExceptionDto>
)

data class AttendanceCounts(
    val scheduledCount: Int,
    val checkedInCount: Int,
    val notCheckedInCount: Int,
    val notCheckedOutCount: Int
)
