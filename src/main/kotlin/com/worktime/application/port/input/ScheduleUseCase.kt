package com.worktime.application.port.input

import com.worktime.adapter.input.web.dto.schedule.ScheduleCreateRequest
import com.worktime.adapter.input.web.dto.schedule.ScheduleResponse
import java.time.OffsetDateTime
import java.util.*

/**
 * Schedule UseCase interface (Input Port)
 */
interface ScheduleUseCase {
    
    /**
     * 스케줄 생성 (근로자 요청 또는 고용주 제안)
     */
    fun createSchedule(request: ScheduleCreateRequest): ScheduleResponse
    
    /**
     * 스케줄 승인/확정
     */
    fun confirmSchedule(scheduleId: UUID): ScheduleResponse
    
    /**
     * 스케줄 거절
     */
    fun rejectSchedule(scheduleId: UUID): ScheduleResponse
    
    /**
     * 스케줄 취소
     */
    fun cancelSchedule(scheduleId: UUID): ScheduleResponse
    
    /**
     * 특정 스케줄 조회
     */
    fun getScheduleById(scheduleId: UUID): ScheduleResponse
    
    /**
     * 근로자의 스케줄 목록 조회 (기간별)
     */
    fun getSchedulesByWorker(
        workerId: UUID,
        from: OffsetDateTime?,
        to: OffsetDateTime?
    ): List<ScheduleResponse>
}
