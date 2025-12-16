package com.worktime.application.port.output

import com.worktime.domain.model.WorkLog
import com.worktime.domain.model.Worker
import java.time.OffsetDateTime
import java.util.*

/**
 * WorkLog Repository interface (Output Port)
 */
interface WorkLogRepository {
    
    fun save(workLog: WorkLog): WorkLog
    
    fun findById(id: UUID): WorkLog?
    
    fun findByWorkerAndTimeRange(
        worker: Worker,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<WorkLog>
    
    fun findByTimeRange(
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<WorkLog>
    
    fun findCompletedWorkLogsInRange(
        worker: Worker,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<WorkLog>
    
    fun findCurrentOngoingWorkLog(worker: Worker): WorkLog?
    
    fun hasOngoingWorkLog(worker: Worker): Boolean
    
    /**
     * Find work logs in time range for specific owner
     * 
     * @param ownerId 고용주 ID
     * @param startTime 시작 시간 (inclusive)
     * @param endTime 종료 시간 (exclusive)
     * @return 워크로그 목록
     */
    fun findByOwnerAndTimeRange(
        ownerId: UUID,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<WorkLog>
    
    /**
     * Find ongoing work logs started before the given time (for detecting missing checkout)
     * 
     * @param ownerId 고용주 ID
     * @param thresholdTime 기준 시간 (이 시간 이전에 시작된 ONGOING 기록 조회)
     * @return 워크로그 목록
     */
    fun findOngoingWorkLogsOlderThan(
        ownerId: UUID,
        thresholdTime: OffsetDateTime
    ): List<WorkLog>

    /**
     * Find work logs by schedule IDs (Batch fetch)
     */
    fun findByScheduleIds(scheduleIds: List<UUID>): List<WorkLog>
}
