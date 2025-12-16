package com.worktime.adapter.output.persistence

import com.worktime.domain.model.Schedule
import com.worktime.domain.model.WorkLog
import com.worktime.domain.model.WorkLogStatus
import com.worktime.domain.model.Worker
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

/**
 * JPA Repository for WorkLog entity.
 */
@Repository
interface WorkLogJpaRepository : JpaRepository<WorkLog, UUID> {
    
    /**
     * Find all work logs for a worker
     */
    fun findByWorker(worker: Worker): List<WorkLog>
    
    /**
     * Find work logs for a worker with a specific status
     */
    fun findByWorkerAndStatus(worker: Worker, status: WorkLogStatus): List<WorkLog>
    
    /**
     * Find ongoing work logs for a worker
     */
    fun findByWorkerAndStatusOrderByClockInAtDesc(worker: Worker, status: WorkLogStatus): List<WorkLog>
    
    /**
     * Find work log for a specific schedule
     */
    fun findBySchedule(schedule: Schedule): WorkLog?
    
    /**
     * Find work logs for a worker within a time range
     */
    @Query("""
        SELECT wl FROM WorkLog wl 
        WHERE wl.worker = :worker 
        AND wl.clockInAt >= :startTime 
        AND (wl.clockOutAt IS NULL OR wl.clockOutAt <= :endTime)
        ORDER BY wl.clockInAt DESC
    """)
    fun findByWorkerAndTimeRange(
        @Param("worker") worker: Worker,
        @Param("startTime") startTime: OffsetDateTime,
        @Param("endTime") endTime: OffsetDateTime
    ): List<WorkLog>
    
    /**
     * Find all work logs within a time range (OWNER용)
     */
    @Query("""
        SELECT wl FROM WorkLog wl 
        WHERE wl.clockInAt >= :startTime 
        AND (wl.clockOutAt IS NULL OR wl.clockOutAt <= :endTime)
        ORDER BY wl.clockInAt DESC
    """)
    fun findByTimeRange(
        @Param("startTime") startTime: OffsetDateTime,
        @Param("endTime") endTime: OffsetDateTime
    ): List<WorkLog>
    
    /**
     * Find completed work logs for a worker within a time range
     */
    @Query("""
        SELECT wl FROM WorkLog wl 
        WHERE wl.worker = :worker 
        AND wl.status = 'COMPLETED'
        AND wl.clockInAt >= :startTime 
        AND wl.clockOutAt <= :endTime
        ORDER BY wl.clockInAt DESC
    """)
    fun findCompletedWorkLogsInRange(
        @Param("worker") worker: Worker,
        @Param("startTime") startTime: OffsetDateTime,
        @Param("endTime") endTime: OffsetDateTime
    ): List<WorkLog>
    
    /**
     * Find the current ongoing work log for a worker (if any)
     */
    @Query("""
        SELECT wl FROM WorkLog wl 
        WHERE wl.worker = :worker 
        AND wl.status = 'ONGOING'
        AND wl.clockOutAt IS NULL
        ORDER BY wl.clockInAt DESC
    """)
    fun findCurrentOngoingWorkLog(@Param("worker") worker: Worker): WorkLog?
    
    /**
     * Check if worker has an ongoing work log
     */
    @Query("""
        SELECT CASE WHEN COUNT(wl) > 0 THEN true ELSE false END
        FROM WorkLog wl 
        WHERE wl.worker = :worker 
        AND wl.status = 'ONGOING'
        AND wl.clockOutAt IS NULL
    """)
    fun hasOngoingWorkLog(@Param("worker") worker: Worker): Boolean
    
    /**
     * Find work logs in time range for specific owner
     */
    @Query("""
        SELECT wl FROM WorkLog wl 
        WHERE wl.worker.owner.id = :ownerId
        AND wl.clockInAt >= :startTime 
        AND (wl.clockOutAt IS NULL OR wl.clockOutAt <= :endTime)
        ORDER BY wl.clockInAt DESC
    """)
    fun findByOwnerAndTimeRange(
        @Param("ownerId") ownerId: UUID,
        @Param("startTime") startTime: OffsetDateTime,
        @Param("endTime") endTime: OffsetDateTime
    ): List<WorkLog>
    
    /**
     * Find ongoing work logs started before threshold
     */
    @Query("""
        SELECT wl FROM WorkLog wl 
        WHERE wl.worker.owner.id = :ownerId
        AND wl.status = 'ONGOING'
        AND wl.clockInAt < :thresholdTime
        AND wl.clockOutAt IS NULL
        ORDER BY wl.clockInAt ASC
    """)
    fun findOngoingOlderThan(
        @Param("ownerId") ownerId: UUID,
        @Param("thresholdTime") thresholdTime: OffsetDateTime
    ): List<WorkLog>

    /**
     * Find by schedule IDs
     */
    fun findByScheduleIdIn(scheduleIds: List<UUID>): List<WorkLog>
}
