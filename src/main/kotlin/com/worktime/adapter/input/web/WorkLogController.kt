package com.worktime.adapter.input.web

import com.worktime.adapter.input.web.dto.worklog.ClockInRequest
import com.worktime.adapter.input.web.dto.worklog.ClockOutRequest
import com.worktime.adapter.input.web.dto.worklog.WorkLogResponse
import com.worktime.application.port.input.WorkLogUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.util.*

/**
 * REST Controller for WorkLog management
 */
@RestController
@RequestMapping("/api")
@Tag(name = "WorkLog", description = "출퇴근 기록 관리 API")
class WorkLogController(
    private val workLogUseCase: WorkLogUseCase
) {
    
    @PostMapping("/workers/{workerId}/clock-in")
    @Operation(summary = "출근", description = "근로자의 출근을 기록합니다")
    fun clockIn(
        @PathVariable workerId: UUID,
        @Valid @RequestBody request: ClockInRequest
    ): ResponseEntity<WorkLogResponse> {
        val response = workLogUseCase.clockIn(workerId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
    
    @PostMapping("/workers/{workerId}/clock-out")
    @Operation(summary = "퇴근", description = "근로자의 퇴근을 기록합니다")
    fun clockOut(
        @PathVariable workerId: UUID,
        @Valid @RequestBody request: ClockOutRequest
    ): ResponseEntity<WorkLogResponse> {
        val response = workLogUseCase.clockOut(workerId, request)
        return ResponseEntity.ok(response)
    }
    
    @GetMapping("/work-logs/{workLogId}")
    @Operation(summary = "근무 기록 조회", description = "특정 근무 기록의 상세 정보를 조회합니다")
    fun getWorkLog(
        @PathVariable workLogId: UUID
    ): ResponseEntity<WorkLogResponse> {
        val response = workLogUseCase.getWorkLogById(workLogId)
        return ResponseEntity.ok(response)
    }
    
    @GetMapping("/workers/{workerId}/work-logs")
    @Operation(summary = "근로자 근무 기록 목록 조회", description = "근로자의 근무 기록 목록을 기간별로 조회합니다")
    fun getWorkLogsByWorker(
        @PathVariable workerId: UUID,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: OffsetDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: OffsetDateTime?
    ): ResponseEntity<List<WorkLogResponse>> {
        val workLogs = workLogUseCase.getWorkLogsByWorker(workerId, from, to)
        return ResponseEntity.ok(workLogs)
    }
}
