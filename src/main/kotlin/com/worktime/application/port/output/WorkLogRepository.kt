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
    
    fun findCompletedWorkLogsInRange(
        worker: Worker,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): List<WorkLog>
    
    fun findCurrentOngoingWorkLog(worker: Worker): WorkLog?
    
    fun hasOngoingWorkLog(worker: Worker): Boolean
}
