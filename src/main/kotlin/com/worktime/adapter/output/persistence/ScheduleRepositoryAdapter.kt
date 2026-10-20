package com.worktime.adapter.output.persistence

import com.worktime.application.port.output.ScheduleRepository
import com.worktime.domain.model.Schedule
import com.worktime.domain.model.ScheduleStatus
import com.worktime.domain.model.Worker
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.*

/**
 * Adapter implementation for ScheduleRepository
 */
@Component
class ScheduleRepositoryAdapter(
    private val scheduleJpaRepository: ScheduleJpaRepository,
    private val entityManager: jakarta.persistence.EntityManager
) : ScheduleRepository {
    
    override fun refreshForWrite(schedule: Schedule) {
        entityManager.refresh(schedule, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    }

    override fun save(schedule: Schedule): Schedule {
        return scheduleJpaRepository.save(schedule)
    }
    
    override fun findById(id: UUID): Schedule? {
        return scheduleJpaRepository.findById(id).orElse(null)
    }
    
    override fun findByWorkerAndTimeRange(
        worker: Worker,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<Schedule> {
        return scheduleJpaRepository.findByWorkerAndTimeRange(worker, startTime, endTime)
    }
    
    override fun findByTimeRange(
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<Schedule> {
        return scheduleJpaRepository.findByTimeRange(startTime, endTime)
    }
    
    override fun findByWorkerAndStatusAndTimeRange(
        worker: Worker,
        status: ScheduleStatus,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<Schedule> {
        return scheduleJpaRepository.findByWorkerAndStatusAndTimeRange(worker, status, startTime, endTime)
    }
    
    override fun findConfirmedSchedulesOverlapping(
        worker: Worker,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<Schedule> {
        return scheduleJpaRepository.findConfirmedOverlapping(worker, startTime, endTime)
    }

    override fun countByOwnerAndTimeRangeExcludingCanceledAndRejected(
        ownerId: UUID,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): Long {
        return scheduleJpaRepository.countByOwnerAndTimeRangeExcludingCanceledAndRejected(
            ownerId, startTime, endTime
        )
    }

    override fun findByOwnerAndStatusIn(
        ownerId: UUID,
        statuses: List<ScheduleStatus>
    ): List<Schedule> {
        return scheduleJpaRepository.findByOwnerAndStatusIn(ownerId, statuses)
    }

    override fun findByOwnerAndTimeRange(
        ownerId: UUID,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<Schedule> {
        return scheduleJpaRepository.findByOwnerAndTimeRange(ownerId, startTime, endTime)
    }
}

