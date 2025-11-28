package com.worktime.adapter.input.web.dto.summary

import java.time.LocalDate

/**
 * 일자별 근무 요약
 */
data class DailySummary(
    val date: LocalDate,
    val totalMinutes: Long,
    val workLogCount: Int
)
