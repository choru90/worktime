package com.worktime.adapter.input.web.dto.summary

import com.worktime.adapter.input.web.dto.worklog.WorkLogResponse
import java.time.LocalDate
import java.util.*

/**
 * 근무 요약 응답 DTO
 */
data class WorkSummaryResponse(
    val workerId: UUID,
    val workerName: String,
    val period: PeriodInfo,
    val totalHours: Long,
    val totalMinutes: Long,
    val workLogCount: Int,
    val workLogs: List<WorkLogResponse>,
    val dailySummaries: List<DailySummary>
)

/**
 * 기간 정보
 */
data class PeriodInfo(
    val from: LocalDate,
    val to: LocalDate
)
