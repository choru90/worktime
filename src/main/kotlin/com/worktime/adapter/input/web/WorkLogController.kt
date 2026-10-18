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
    private val workLogUseCase: WorkLogUseCase,
    private val worktimeAccess: com.worktime.application.service.WorktimeAccess
) {
    
    @PostMapping("/workers/{workerId}/clock-in")
    @org.springframework.security.access.prepost.PreAuthorize("@worktimeAccess.worker(#workerId)")
    @Operation(summary = "출근", description = "근로자의 출근을 기록합니다")
    fun clockIn(
        @PathVariable workerId: UUID,
        @Valid @RequestBody request: ClockInRequest
    ): ResponseEntity<WorkLogResponse> {
        val response = workLogUseCase.clockIn(workerId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
    
    @PostMapping("/workers/{workerId}/clock-out")
    @org.springframework.security.access.prepost.PreAuthorize("@worktimeAccess.worker(#workerId)")
    @Operation(summary = "퇴근", description = "근로자의 퇴근을 기록합니다")
    fun clockOut(
        @PathVariable workerId: UUID,
        @Valid @RequestBody request: ClockOutRequest
    ): ResponseEntity<WorkLogResponse> {
        val response = workLogUseCase.clockOut(workerId, request)
        return ResponseEntity.ok(response)
    }
    
    @GetMapping("/work-logs/{workLogId}")
    @org.springframework.security.access.prepost.PreAuthorize("@worktimeAccess.log(#workLogId)")
    @Operation(summary = "근무 기록 조회", description = "특정 근무 기록의 상세 정보를 조회합니다")
    fun getWorkLog(
        @PathVariable workLogId: UUID
    ): ResponseEntity<WorkLogResponse> {
        val response = workLogUseCase.getWorkLogById(workLogId)
        return ResponseEntity.ok(response)
    }
    
    @GetMapping("/workers/{workerId}/work-logs")
    @org.springframework.security.access.prepost.PreAuthorize("@worktimeAccess.worker(#workerId)")
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
    
    @GetMapping("/work-logs")
    @Operation(summary = "전체 근무 기록 조회", description = "모든 근로자의 근무 기록을 조회합니다 (OWNER 전용)")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('OWNER')")
    fun getAllWorkLogs(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: OffsetDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: OffsetDateTime?,
        @RequestParam(required = false)
        workerId: UUID?,
        @RequestParam(required = false)
        status: com.worktime.domain.model.WorkLogStatus?
    ): ResponseEntity<List<WorkLogResponse>> {
        if (workerId != null) worktimeAccess.worker(workerId)
        val workLogs = workLogUseCase.getAllWorkLogs(from, to, workerId, status, worktimeAccess.ownerId())
        return ResponseEntity.ok(workLogs)
    }
    
    @PostMapping("/work-logs/{workLogId}/confirm")
    @Operation(summary = "근무 기록 확정", description = "근무 기록을 확정합니다 (OWNER 전용)")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('OWNER') and @worktimeAccess.log(#workLogId)")
    fun confirmWorkLog(
        @PathVariable workLogId: UUID
    ): ResponseEntity<WorkLogResponse> {
        val response = workLogUseCase.confirmWorkLog(workLogId)
        return ResponseEntity.ok(response)
    }
    
    @PostMapping("/work-logs/{workLogId}/reject")
    @Operation(summary = "근무 기록 반려", description = "근무 기록을 반려합니다 (OWNER 전용)")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('OWNER') and @worktimeAccess.log(#workLogId)")
    fun rejectWorkLog(
        @PathVariable workLogId: UUID,
        @Valid @RequestBody(required = false) request: com.worktime.adapter.input.web.dto.worklog.WorkLogRejectRequest?
    ): ResponseEntity<WorkLogResponse> {
        val response = workLogUseCase.rejectWorkLog(workLogId, request?.reason)
        return ResponseEntity.ok(response)
    }
}
