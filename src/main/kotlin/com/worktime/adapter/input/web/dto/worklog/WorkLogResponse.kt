package com.worktime.adapter.input.web.dto.worklog

import com.worktime.domain.model.WorkLog
import com.worktime.domain.model.WorkLogStatus
import java.time.OffsetDateTime
import java.util.*

/**
 * WorkLog 응답 DTO
 */
data class WorkLogResponse(
    val id: UUID,
    val workerId: UUID,
    val workerName: String,
    val scheduleId: UUID?,
    val clockInAt: OffsetDateTime,
    val clockOutAt: OffsetDateTime?,
    val status: WorkLogStatus,
    val durationMinutes: Long?,
    val createdAt: String,
    val updatedAt: String,
    val correctionCount: Int = 0, val correctionReason: String? = null, val rejectionReason: String? = null
) {
    companion object {
        fun from(workLog: WorkLog): WorkLogResponse {
            val duration = workLog.calculateDuration()
            return WorkLogResponse(
                id = workLog.id,
                workerId = workLog.worker.id,
                workerName = workLog.worker.name,
                scheduleId = workLog.schedule?.id,
                clockInAt = workLog.clockInAt,
                clockOutAt = workLog.clockOutAt,
                status = workLog.status,
                durationMinutes = duration?.toMinutes(),
                createdAt = workLog.createdAt.toString(),
                updatedAt = workLog.updatedAt.toString(), correctionCount = workLog.correctionCount, correctionReason = workLog.correctionReason, rejectionReason = workLog.rejectionReason
            )
        }
    }
}

