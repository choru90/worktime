package com.worktime.adapter.output.persistence

import com.worktime.domain.model.Schedule
import com.worktime.domain.model.ScheduleStatus
import com.worktime.domain.model.Worker
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

/**
 * JPA Repository for Schedule entity.
 */
@Repository
interface ScheduleJpaRepository : JpaRepository<Schedule, UUID> {
    
    /**
     * Find all schedules for a worker
     */
    fun findByWorker(worker: Worker): List<Schedule>
    
    /**
     * Find schedules for a worker with a specific status
     */
    fun findByWorkerAndStatus(worker: Worker, status: ScheduleStatus): List<Schedule>
    
    /**
     * Find confirmed schedules for a worker
     */
    fun findByWorkerAndStatusOrderByStartAtUtcAsc(worker: Worker, status: ScheduleStatus): List<Schedule>
    
    /**
     * Find schedules for a worker within a time range
     */
    @Query("""
        SELECT s FROM Schedule s 
        WHERE s.worker = :worker 
        AND s.startAtUtc >= :startTime 
        AND s.endAtUtc <= :endTime
        ORDER BY s.startAtUtc ASC
    """)
    fun findByWorkerAndTimeRange(
        @Param("worker") worker: Worker,
        @Param("startTime") startTime: OffsetDateTime,
        @Param("endTime") endTime: OffsetDateTime
    ): List<Schedule>
    
    /**
     * Find all schedules within a time range (OWNER용)
     */
    @Query("""
        SELECT s FROM Schedule s 
        WHERE s.startAtUtc >= :startTime 
        AND s.endAtUtc <= :endTime
        ORDER BY s.startAtUtc ASC
    """)
    fun findByTimeRange(
        @Param("startTime") startTime: OffsetDateTime,
        @Param("endTime") endTime: OffsetDateTime
    ): List<Schedule>
    
    /**
     * Find schedules for a worker within a time range with specific status
     */
    @Query("""
        SELECT s FROM Schedule s 
        WHERE s.worker = :worker 
        AND s.status = :status
        AND s.startAtUtc >= :startTime 
        AND s.endAtUtc <= :endTime
        ORDER BY s.startAtUtc ASC
    """)
    fun findByWorkerAndStatusAndTimeRange(
        @Param("worker") worker: Worker,
        @Param("status") status: ScheduleStatus,
        @Param("startTime") startTime: OffsetDateTime,
        @Param("endTime") endTime: OffsetDateTime
    ): List<Schedule>
    
    /**
     * Find pending schedules (requested or proposed) for a worker
     */
    @Query("""
        SELECT s FROM Schedule s 
        WHERE s.worker = :worker 
        AND s.status IN ('REQUESTED_BY_WORKER', 'PROPOSED_BY_OWNER')
        ORDER BY s.startAtUtc ASC
    """)
    fun findPendingSchedules(@Param("worker") worker: Worker): List<Schedule>
    
    /**
     * Count schedules in time range excluding canceled/rejected for specific owner
     */
    @Query("""
        SELECT COUNT(s) FROM Schedule s 
        WHERE s.worker.owner.id = :ownerId 
        AND s.startAtUtc >= :startTime 
        AND s.startAtUtc < :endTime
        AND s.status NOT IN ('CANCELED', 'REJECTED')
    """)
    fun countByOwnerAndTimeRangeExcludingCanceledAndRejected(
        @Param("ownerId") ownerId: UUID,
        @Param("startTime") startTime: OffsetDateTime,
        @Param("endTime") endTime: OffsetDateTime
    ): Long
    
    /**
     * Find schedules by status list for specific owner
     */
    @Query("""
        SELECT s FROM Schedule s 
        WHERE s.worker.owner.id = :ownerId
        AND s.status IN :statuses
        ORDER BY s.startAtUtc ASC
    """)
    fun findByOwnerAndStatusIn(
        @Param("ownerId") ownerId: UUID,
        @Param("statuses") statuses: List<ScheduleStatus>
    ): List<Schedule>
}
