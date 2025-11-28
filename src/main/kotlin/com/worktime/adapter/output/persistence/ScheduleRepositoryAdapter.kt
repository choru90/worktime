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
    private val scheduleJpaRepository: ScheduleJpaRepository
) : ScheduleRepository {
    
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
        // Find CONFIRMED schedules that overlap with the given time range
        return scheduleJpaRepository.findByWorkerAndStatusAndTimeRange(
            worker = worker,
            status = ScheduleStatus.CONFIRMED,
            startTime = startTime.minusDays(1), // Expand search range to catch overlaps
            endTime = endTime.plusDays(1)
        ).filter { schedule ->
            // Check if the schedules actually overlap
            !(schedule.endAtUtc.isBefore(startTime) || schedule.startAtUtc.isAfter(endTime))
        }
    }
}
