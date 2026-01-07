package com.worktime.adapter.input.web

import com.worktime.adapter.input.web.dto.worklog.ClockInRequest
import com.worktime.adapter.input.web.dto.worklog.ClockOutRequest
import com.worktime.adapter.input.web.dto.worklog.WorkLogResponse
import com.worktime.application.port.input.WorkLogUseCase
import com.worktime.application.port.output.UserRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.exception.ResourceNotFoundException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * 알바생 전용 출퇴근 기록 API Controller
 */
@RestController
@RequestMapping("/api/worklogs/my")
@Tag(name = "My WorkLog", description = "알바생 본인 출퇴근 기록 API")
@PreAuthorize("hasRole('WORKER')")
class WorkerWorkLogController(
    private val workLogUseCase: WorkLogUseCase,
    private val userRepository: UserRepository,
    private val workerRepository: WorkerRepository,
    private val scheduleUseCase: com.worktime.application.port.input.ScheduleUseCase
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
    @Operation(summary = "내 근무 기록 조회", description = "로그인한 알바생의 근무 기록을 조회합니다")
    fun getMyWorkLogs(
        @RequestParam(required = false) fromDate: String?,
        @RequestParam(required = false) toDate: String?
    ): ResponseEntity<List<WorkLogResponse>> {
        val workerId = getCurrentWorkerId()
        
        val from = fromDate?.let { 
            LocalDate.parse(it).atStartOfDay().atOffset(ZoneOffset.UTC) 
        }
        val to = toDate?.let { 
            LocalDate.parse(it).plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC) 
        }
        
        val workLogs = workLogUseCase.getWorkLogsByWorker(workerId, from, to)
        return ResponseEntity.ok(workLogs)
    }
    
    @GetMapping("/current")
    @Operation(summary = "현재 진행중인 근무 조회", description = "현재 출근 상태인 근무 기록을 조회합니다")
    fun getCurrentWorkLog(): ResponseEntity<WorkLogResponse?> {
        val workerId = getCurrentWorkerId()
        val currentWorkLog = workLogUseCase.getCurrentOngoingWorkLog(workerId)
        return ResponseEntity.ok(currentWorkLog)
    }
    
    @PostMapping("/clock-in")
    @Operation(summary = "출근", description = "현재 시간으로 출근 처리합니다")
    fun clockIn(): ResponseEntity<WorkLogResponse> {
        val workerId = getCurrentWorkerId()
        val now = OffsetDateTime.now()
        
        // 오늘의 확정 스케줄 찾기
        val todayStart = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        val todayEnd = todayStart.plusDays(1)
        
        val todaySchedules = scheduleUseCase.getSchedulesByWorker(workerId, todayStart, todayEnd)
            .filter { it.status.name == "CONFIRMED" }
        
        // 현재 시간과 가장 가까운 스케줄 선택
        val matchingSchedule = todaySchedules.minByOrNull { schedule ->
            val startTime = schedule.startAtUtc
            kotlin.math.abs(java.time.Duration.between(now, startTime).toMinutes())
        }
        
        val request = ClockInRequest(
            clockInAt = now,
            scheduleId = matchingSchedule?.id
        )
        
        val response = workLogUseCase.clockIn(workerId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
    
    @PostMapping("/clock-out")
    @Operation(summary = "퇴근", description = "현재 시간으로 퇴근 처리합니다")
    fun clockOut(): ResponseEntity<WorkLogResponse> {
        val workerId = getCurrentWorkerId()
        
        val request = ClockOutRequest(
            clockOutAt = OffsetDateTime.now()
        )
        
        val response = workLogUseCase.clockOut(workerId, request)
        return ResponseEntity.ok(response)
    }
}
