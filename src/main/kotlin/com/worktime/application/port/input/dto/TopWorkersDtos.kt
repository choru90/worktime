package com.worktime.application.port.input.dto

import java.math.BigDecimal
import java.time.LocalDate

data class TopWorkersResponse(
    val timezone: String = "Asia/Seoul",
    val range: DateRangeDto,
    val items: List<TopWorkerItem>
)

data class DateRangeDto(
    val from: LocalDate,
    val to: LocalDate,
    val days: Long
)

data class TopWorkerItem(
    val rank: Int,
    val worker: WorkerInfo,
    val stats: WorkerStats,
    val badges: List<WorkerBadge> = emptyList()
)

data class WorkerInfo(
    val workerId: String,
    val name: String,
    val pictureUrl: String? = null
)

data class WorkerStats(
    val completedShifts: Int,
    val totalWorkMinutes: Long,
    val accruedWage: BigDecimal
)

enum class TopWorkerSort {
    WAGE_DESC,
    MINUTES_DESC,
    SHIFTS_DESC
}

enum class WorkerBadge {
    TOP_WAGE,
    TOP_MINUTES
}
