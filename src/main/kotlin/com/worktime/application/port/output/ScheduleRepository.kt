package com.worktime.application.port.output

import com.worktime.domain.model.Schedule
import com.worktime.domain.model.ScheduleStatus
import com.worktime.domain.model.Worker
import java.time.OffsetDateTime
import java.util.*

/**
 * Schedule Repository interface (Output Port)
 */
interface ScheduleRepository {
    
    fun save(schedule: Schedule): Schedule
    
    fun findById(id: UUID): Schedule?
    
    fun findByWorkerAndTimeRange(
        worker: Worker,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<Schedule>
    
    fun findByTimeRange(
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<Schedule>
    
    fun findByWorkerAndStatusAndTimeRange(
        worker: Worker,
        status: ScheduleStatus,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<Schedule>
    
    /**
     * Find confirmed schedules that overlap with the given time range
     */
    fun findConfirmedSchedulesOverlapping(
        worker: Worker,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<Schedule>
}
