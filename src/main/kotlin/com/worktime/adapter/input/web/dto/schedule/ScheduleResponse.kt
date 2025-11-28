package com.worktime.adapter.input.web.dto.schedule

import com.worktime.domain.model.Schedule
import com.worktime.domain.model.ScheduleStatus
import java.time.OffsetDateTime
import java.util.*

/**
 * 스케줄 응답 DTO
 */
data class ScheduleResponse(
    val id: UUID,
    val workerId: UUID,
    val workerName: String,
    val startAtUtc: OffsetDateTime,
    val endAtUtc: OffsetDateTime,
    val timezone: String,
    val status: ScheduleStatus,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(schedule: Schedule): ScheduleResponse {
            return ScheduleResponse(
                id = schedule.id,
                workerId = schedule.worker.id,
                workerName = schedule.worker.name,
                startAtUtc = schedule.startAtUtc,
                endAtUtc = schedule.endAtUtc,
                timezone = schedule.timezone,
                status = schedule.status,
                createdAt = schedule.createdAt.toString(),
                updatedAt = schedule.updatedAt.toString()
            )
        }
    }
}
