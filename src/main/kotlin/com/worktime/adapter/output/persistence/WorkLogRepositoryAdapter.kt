package com.worktime.adapter.output.persistence

import com.worktime.application.port.output.WorkLogRepository
import com.worktime.domain.model.WorkLog
import com.worktime.domain.model.Worker
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.*

/**
 * Adapter implementation for WorkLogRepository
 */
@Component
class WorkLogRepositoryAdapter(
    private val workLogJpaRepository: WorkLogJpaRepository
) : WorkLogRepository {
    
    override fun save(workLog: WorkLog): WorkLog {
        return workLogJpaRepository.save(workLog)
    }
    
    override fun findById(id: UUID): WorkLog? {
        return workLogJpaRepository.findById(id).orElse(null)
    }
    
    override fun findByWorkerAndTimeRange(
        worker: Worker,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<WorkLog> {
        return workLogJpaRepository.findByWorkerAndTimeRange(worker, startTime, endTime)
    }
    
    override fun findByTimeRange(
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<WorkLog> {
        return workLogJpaRepository.findByTimeRange(startTime, endTime)
    }
    
    override fun findCompletedWorkLogsInRange(
        worker: Worker,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<WorkLog> {
        return workLogJpaRepository.findCompletedWorkLogsInRange(worker, startTime, endTime)
    }
    
    override fun findCurrentOngoingWorkLog(worker: Worker): WorkLog? {
        return workLogJpaRepository.findCurrentOngoingWorkLog(worker)
    }
    
    override fun hasOngoingWorkLog(worker: Worker): Boolean {
        return workLogJpaRepository.hasOngoingWorkLog(worker)
    }

    override fun findByOwnerAndTimeRange(
        ownerId: UUID,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<WorkLog> {
        return workLogJpaRepository.findByOwnerAndTimeRange(ownerId, startTime, endTime)
    }

    override fun findOngoingWorkLogsOlderThan(
        ownerId: UUID,
        thresholdTime: OffsetDateTime
    ): List<WorkLog> {
        return workLogJpaRepository.findOngoingOlderThan(ownerId, thresholdTime)
    }

    override fun findByScheduleIds(scheduleIds: List<UUID>): List<WorkLog> {
        if (scheduleIds.isEmpty()) return emptyList()
        return workLogJpaRepository.findByScheduleIdIn(scheduleIds)
    }

    override fun findOngoingWorkLogsByOwner(ownerId: UUID): List<WorkLog> {
        return workLogJpaRepository.findOngoingWorkLogsByOwner(ownerId)
    }
}
