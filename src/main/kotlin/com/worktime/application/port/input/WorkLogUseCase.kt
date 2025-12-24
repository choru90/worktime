package com.worktime.application.port.input

import com.worktime.adapter.input.web.dto.worklog.ClockInRequest
import com.worktime.adapter.input.web.dto.worklog.ClockOutRequest
import com.worktime.adapter.input.web.dto.worklog.WorkLogResponse
import com.worktime.domain.model.WorkLogStatus
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
    
    /**
     * 전체 근무 기록 조회 (OWNER용, 필터 지원)
     */
    fun getAllWorkLogs(
        from: OffsetDateTime?,
        to: OffsetDateTime?,
        workerId: UUID?,
        status: WorkLogStatus?
    ): List<WorkLogResponse>
    
    /**
     * 근무 기록 확정 (OWNER가 검수 완료)
     */
    fun confirmWorkLog(workLogId: UUID): WorkLogResponse
    
    /**
     * 근무 기록 반려 (OWNER가 거절)
     */
    fun rejectWorkLog(workLogId: UUID, reason: String?): WorkLogResponse
    
    /**
     * 근로자의 현재 진행중인 근무 기록 조회
     */
    fun getCurrentOngoingWorkLog(workerId: UUID): WorkLogResponse?
}
