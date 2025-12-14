package com.worktime.adapter.input.web

import com.worktime.adapter.input.web.dto.schedule.ScheduleCreateRequest
import com.worktime.adapter.input.web.dto.schedule.ScheduleListResponse
import com.worktime.adapter.input.web.dto.schedule.ScheduleResponse
import com.worktime.application.port.input.ScheduleUseCase
import com.worktime.domain.model.ScheduleStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.util.*

/**
 * REST Controller for Schedule management
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Schedule", description = "근무 스케줄 관리 API")
class ScheduleController(
    private val scheduleUseCase: ScheduleUseCase
) {
    
    @PostMapping("/workers/{workerId}/schedules")
    @Operation(summary = "스케줄 생성", description = "근로자 요청 또는 고용주 제안으로 스케줄을 생성합니다")
    fun createSchedule(
        @PathVariable workerId: UUID,
        @Valid @RequestBody request: ScheduleCreateRequest
    ): ResponseEntity<ScheduleResponse> {
        val response = scheduleUseCase.createSchedule(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
    
    @PostMapping("/schedules/{scheduleId}/confirm")
    @Operation(summary = "스케줄 승인", description = "요청되거나 제안된 스케줄을 승인/확정합니다")
    fun confirmSchedule(
        @PathVariable scheduleId: UUID
    ): ResponseEntity<ScheduleResponse> {
        val response = scheduleUseCase.confirmSchedule(scheduleId)
        return ResponseEntity.ok(response)
    }
    
    @PostMapping("/schedules/{scheduleId}/reject")
    @Operation(summary = "스케줄 거절", description = "요청되거나 제안된 스케줄을 거절합니다")
    fun rejectSchedule(
        @PathVariable scheduleId: UUID
    ): ResponseEntity<ScheduleResponse> {
        val response = scheduleUseCase.rejectSchedule(scheduleId)
        return ResponseEntity.ok(response)
    }
    
    @PostMapping("/schedules/{scheduleId}/cancel")
    @Operation(summary = "스케줄 취소", description = "확정된 스케줄을 취소합니다")
    fun cancelSchedule(
        @PathVariable scheduleId: UUID
    ): ResponseEntity<ScheduleResponse> {
        val response = scheduleUseCase.cancelSchedule(scheduleId)
        return ResponseEntity.ok(response)
    }
    
    @GetMapping("/schedules/{scheduleId}")
    @Operation(summary = "스케줄 조회", description = "특정 스케줄의 상세 정보를 조회합니다")
    fun getSchedule(
        @PathVariable scheduleId: UUID
    ): ResponseEntity<ScheduleResponse> {
        val response = scheduleUseCase.getScheduleById(scheduleId)
        return ResponseEntity.ok(response)
    }
    
    @GetMapping("/workers/{workerId}/schedules")
    @Operation(summary = "근로자 스케줄 목록 조회", description = "근로자의 스케줄 목록을 기간별로 조회합니다")
    fun getSchedulesByWorker(
        @PathVariable workerId: UUID,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: OffsetDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: OffsetDateTime?
    ): ResponseEntity<ScheduleListResponse> {
        val schedules = scheduleUseCase.getSchedulesByWorker(workerId, from, to)
        val response = ScheduleListResponse.from(schedules)
        return ResponseEntity.ok(response)
    }
    
    @GetMapping("/schedules")
    @Operation(summary = "전체 스케줄 조회", description = "모든 근로자의 스케줄을 조회합니다 (OWNER 전용)")
    @PreAuthorize("hasRole('OWNER')")
    fun getAllSchedules(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: OffsetDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: OffsetDateTime?,
        @RequestParam(required = false)
        workerId: UUID?,
        @RequestParam(required = false)
        status: ScheduleStatus?
    ): ResponseEntity<ScheduleListResponse> {
        val schedules = scheduleUseCase.getAllSchedules(from, to, workerId, status)
        val response = ScheduleListResponse.from(schedules)
        return ResponseEntity.ok(response)
    }
}
