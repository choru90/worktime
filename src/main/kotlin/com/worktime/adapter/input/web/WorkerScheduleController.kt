package com.worktime.adapter.input.web

import com.worktime.adapter.input.web.dto.schedule.ScheduleListResponse
import com.worktime.adapter.input.web.dto.schedule.ScheduleResponse
import com.worktime.application.port.input.ScheduleUseCase
import com.worktime.application.port.output.UserRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.exception.ResourceNotFoundException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

/**
 * 알바생 전용 스케줄 조회 API Controller
 */
@RestController
@RequestMapping("/api/worker/schedules")
@Tag(name = "My Schedule", description = "알바생 본인 스케줄 API")
@PreAuthorize("hasRole('WORKER')")
class WorkerScheduleController(
    private val scheduleUseCase: ScheduleUseCase,
    private val userRepository: UserRepository,
    private val workerRepository: WorkerRepository
) {
    
    /**
     * 현재 로그인한 사용자의 Worker ID를 가져옵니다.
     */
    private fun getCurrentWorkerId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
        val userId = UUID.fromString(authentication.name)
        
        val user = userRepository.findById(userId)
            ?: throw ResourceNotFoundException("User not found with id: $userId")
        
        // Worker 엔티티에서 userAccount로 연결된 Worker를 찾음
        val worker = workerRepository.findByUserAccountId(userId)
            ?: throw ResourceNotFoundException("연결된 알바생 정보가 없습니다. 사장님에게 문의하세요.")
        
        return worker.id
    }
    
    @GetMapping
    @Operation(summary = "내 스케줄 조회", description = "로그인한 알바생의 스케줄 목록을 조회합니다")
    fun getMySchedules(
        @RequestParam(required = false) 
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: OffsetDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: OffsetDateTime?
    ): ResponseEntity<ScheduleListResponse> {
        val workerId = getCurrentWorkerId()
        
        val schedules = scheduleUseCase.getSchedulesByWorker(workerId, from, to)
        val response = ScheduleListResponse.from(schedules)
        return ResponseEntity.ok(response)
    }
    
    @PostMapping("/{scheduleId}/confirm")
    @Operation(summary = "스케줄 승인", description = "사장님이 제안한 스케줄을 승인합니다")
    fun confirmSchedule(
        @PathVariable scheduleId: UUID
    ): ResponseEntity<ScheduleResponse> {
        // TODO: 본인 스케줄인지 검증 필요
        val response = scheduleUseCase.confirmSchedule(scheduleId)
        return ResponseEntity.ok(response)
    }
    
    @PostMapping("/{scheduleId}/reject")
    @Operation(summary = "스케줄 거절", description = "사장님이 제안한 스케줄을 거절합니다")
    fun rejectSchedule(
        @PathVariable scheduleId: UUID
    ): ResponseEntity<ScheduleResponse> {
        // TODO: 본인 스케줄인지 검증 필요
        val response = scheduleUseCase.rejectSchedule(scheduleId)
        return ResponseEntity.ok(response)
    }
}
