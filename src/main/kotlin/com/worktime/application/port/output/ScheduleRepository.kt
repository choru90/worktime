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
    
    /** Refresh state after acquiring the worker lock, within the same transaction. */
    fun refreshForWrite(schedule: Schedule) {}

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
    
    /**
     * Count schedules in time range excluding canceled/rejected for specific owner
     * 
     * @param ownerId 고용주 ID
     * @param startTime 시작 시간 (inclusive)
     * @param endTime 종료 시간 (exclusive)
     * @return 스케줄 개수
     */
    fun countByOwnerAndTimeRangeExcludingCanceledAndRejected(
        ownerId: UUID,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): Long
    
    /**
     * Find schedules by status list for specific owner
     * 
     * @param ownerId 고용주 ID
     * @param statuses 조회할 상태 목록
     * @return 스케줄 목록
     */
    fun findByOwnerAndStatusIn(
        ownerId: UUID,
        statuses: List<ScheduleStatus>
    ): List<Schedule>

    /**
     * Find schedules for a specific owner within a time range
     * 
     * @param ownerId 고용주 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 스케줄 목록
     */
    fun findByOwnerAndTimeRange(
        ownerId: UUID,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<Schedule>
}

