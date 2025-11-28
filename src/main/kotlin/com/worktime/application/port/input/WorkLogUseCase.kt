package com.worktime.application.port.input

import com.worktime.adapter.input.web.dto.worklog.ClockInRequest
import com.worktime.adapter.input.web.dto.worklog.ClockOutRequest
import com.worktime.adapter.input.web.dto.worklog.WorkLogResponse
import java.time.OffsetDateTime
import java.util.*

/**
 * WorkLog UseCase interface (Input Port)
 */
interface WorkLogUseCase {
    
    /**
     * 근로자 출근
     */
    fun clockIn(workerId: UUID, request: ClockInRequest): WorkLogResponse
    
    /**
     * 근로자 퇴근
     */
    fun clockOut(workerId: UUID, request: ClockOutRequest): WorkLogResponse
    
    /**
     * 특정 근무 기록 조회
     */
    fun getWorkLogById(workLogId: UUID): WorkLogResponse
    
    /**
     * 근로자의 근무 기록 목록 조회 (기간별)
     */
    fun getWorkLogsByWorker(
        workerId: UUID,
        from: OffsetDateTime?,
        to: OffsetDateTime?
    ): List<WorkLogResponse>
}
