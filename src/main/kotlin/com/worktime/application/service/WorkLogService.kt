package com.worktime.application.service

import com.worktime.adapter.input.web.dto.worklog.ClockInRequest
import com.worktime.adapter.input.web.dto.worklog.ClockOutRequest
import com.worktime.adapter.input.web.dto.worklog.ManualWorkLogRequest
import com.worktime.adapter.input.web.dto.worklog.WorkLogResponse
import com.worktime.application.port.input.WorkLogUseCase
import com.worktime.application.port.output.ScheduleRepository
import com.worktime.application.port.output.WorkLogRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.exception.BusinessException
import com.worktime.domain.exception.InvalidWorkLogStateException
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.exception.WorkLogConflictException
import com.worktime.domain.model.ScheduleStatus
import com.worktime.domain.model.WorkLog
import com.worktime.domain.model.WorkLogStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

/**
 * Service implementing WorkLog business logic.
 */
@Service
@Transactional
class WorkLogService(
    private val workLogRepository: WorkLogRepository,
    private val workerRepository: WorkerRepository,
    private val scheduleRepository: ScheduleRepository
) : WorkLogUseCase {
    
    override fun clockIn(workerId: UUID, request: ClockInRequest): WorkLogResponse {
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        
        // Check if worker already has an ongoing work log
        if (workLogRepository.hasOngoingWorkLog(worker)) {
            throw WorkLogConflictException(
                "Worker already has an ongoing work log. Please clock out before clocking in again."
            )
        }
        
        // Optionally validate schedule if provided
        val schedule = request.scheduleId?.let { scheduleId ->
            scheduleRepository.findById(scheduleId)
                ?: throw ResourceNotFoundException("Schedule not found with id: $scheduleId")
        }
        
        val workLog = WorkLog(
            worker = worker,
            schedule = schedule,
            clockInAt = request.clockInAt
        )
        
        val savedWorkLog = workLogRepository.save(workLog)
        return WorkLogResponse.from(savedWorkLog)
    }
    
    override fun clockOut(workerId: UUID, request: ClockOutRequest): WorkLogResponse {
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        
        // Find the current ongoing work log
        val workLog = workLogRepository.findCurrentOngoingWorkLog(worker)
            ?: throw InvalidWorkLogStateException(
                "No ongoing work log found for worker. Please clock in first."
            )
        
        // Validate clock out time
        if (request.clockOutAt.isBefore(workLog.clockInAt) || request.clockOutAt.isEqual(workLog.clockInAt)) {
            throw InvalidWorkLogStateException(
                "Clock-out time must be after clock-in time (${workLog.clockInAt})"
            )
        }
        
        try {
            workLog.clockOut(request.clockOutAt)
        } catch (e: IllegalArgumentException) {
            throw InvalidWorkLogStateException(e.message ?: "Cannot clock out in current state")
        }
        
        val savedWorkLog = workLogRepository.save(workLog)
        return WorkLogResponse.from(savedWorkLog)
    }
    
    override fun createManualWorkLog(workerId: UUID, scheduleId: UUID, request: ManualWorkLogRequest): WorkLogResponse {
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        
        val schedule = scheduleRepository.findById(scheduleId)
            ?: throw ResourceNotFoundException("Schedule not found with id: $scheduleId")
        
        // 스케줄이 현재 worker 소유인지 확인
        if (schedule.worker.id != workerId) {
            throw BusinessException("이 스케줄은 현재 알바생의 것이 아닙니다")
        }
        
        // 스케줄이 CONFIRMED 상태인지 확인
        if (schedule.status != ScheduleStatus.CONFIRMED) {
            throw BusinessException("확정된 스케줄에만 근무 기록을 작성할 수 있습니다")
        }
        
        // 이 스케줄에 이미 근무 기록이 있는지 확인
        // (TODO: 필요시 스케줄ID로 조회하는 repository 메서드 추가)
        
        // WorkLog 생성 (출퇴근 완료 상태로 바로 생성)
        val workLog = WorkLog(
            worker = worker,
            schedule = schedule,
            clockInAt = request.clockInAt
        )
        workLog.clockOut(request.clockOutAt)  // COMPLETED 상태로 변경
        
        val savedWorkLog = workLogRepository.save(workLog)
        return WorkLogResponse.from(savedWorkLog)
    }
    
    override fun getWorkLogById(workLogId: UUID): WorkLogResponse {
        val workLog = workLogRepository.findById(workLogId)
            ?: throw ResourceNotFoundException("WorkLog not found with id: $workLogId")
        return WorkLogResponse.from(workLog)
    }
    
    override fun getWorkLogsByWorker(
        workerId: UUID,
        from: OffsetDateTime?,
        to: OffsetDateTime?
    ): List<WorkLogResponse> {
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        
        val workLogs = if (from != null && to != null) {
            workLogRepository.findByWorkerAndTimeRange(worker, from, to)
        } else {
            // If no date range specified, return empty list or implement a default range
            emptyList()
        }
        
        return workLogs.map { WorkLogResponse.from(it) }
    }
    
    override fun getAllWorkLogs(
        from: OffsetDateTime?,
        to: OffsetDateTime?,
        workerId: UUID?,
        status: WorkLogStatus?
    ): List<WorkLogResponse> {
        // Filter by workerId if provided
        val workLogList = when {
            workerId != null -> {
                val worker = workerRepository.findById(workerId)
                    ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
                
                if (from != null && to != null) {
                    workLogRepository.findByWorkerAndTimeRange(worker, from, to)
                } else {
                    emptyList()
                }
            }
            from != null && to != null -> {
                workLogRepository.findByTimeRange(from, to)
            }
            else -> emptyList()
        }
        
        val syntheticNoShowLogList = if (from != null && to != null) {
            val scheduleList = if (workerId != null) {
                val worker = workerRepository.findById(workerId)
                if (worker != null) {
                    scheduleRepository.findByWorkerAndTimeRange(worker, from, to)
                } else {
                    emptyList()
                }
            } else {
                scheduleRepository.findByTimeRange(from, to)
            }
            
            val ongoingOrCompletedScheduleIdList = workLogList.mapNotNull { it.schedule?.id }.toSet()
            val currentOffsetDateTime = OffsetDateTime.now()
            
            scheduleList
                .filter { it.status !in listOf(ScheduleStatus.CANCELED, ScheduleStatus.REJECTED) && it.startAtUtc.isBefore(currentOffsetDateTime) }
                .filter { it.id !in ongoingOrCompletedScheduleIdList }
                .map { schedule ->
                    WorkLogResponse(
                        id = schedule.id,
                        workerId = schedule.worker.id,
                        workerName = schedule.worker.name,
                        scheduleId = schedule.id,
                        clockInAt = schedule.startAtUtc,
                        clockOutAt = null,
                        status = WorkLogStatus.NO_SHOW,
                        durationMinutes = null,
                        createdAt = schedule.createdAt.toString(),
                        updatedAt = schedule.updatedAt.toString()
                    )
                }
        } else {
            emptyList()
        }

        val allWorkLogResponseList = workLogList.map { WorkLogResponse.from(it) } + syntheticNoShowLogList
        
        val filteredLogList = if (status != null) {
            allWorkLogResponseList.filter { it.status == status }
        } else {
            allWorkLogResponseList
        }
        
        return filteredLogList.sortedByDescending { it.clockInAt }
    }
    
    override fun confirmWorkLog(workLogId: UUID): WorkLogResponse {
        val workLog = workLogRepository.findById(workLogId)
            ?: throw ResourceNotFoundException("WorkLog not found with id: $workLogId")
        
        try {
            workLog.confirm()
        } catch (e: IllegalStateException) {
            throw InvalidWorkLogStateException(e.message ?: "Cannot confirm work log in current state")
        }
        
        val savedWorkLog = workLogRepository.save(workLog)
        return WorkLogResponse.from(savedWorkLog)
    }
    
    override fun rejectWorkLog(workLogId: UUID, reason: String?): WorkLogResponse {
        val workLog = workLogRepository.findById(workLogId)
            ?: throw ResourceNotFoundException("WorkLog not found with id: $workLogId")
        
        try {
            workLog.reject()
        } catch (e: IllegalStateException) {
            throw InvalidWorkLogStateException(e.message ?: "Cannot reject work log in current state")
        }
        
        val savedWorkLog = workLogRepository.save(workLog)
        return WorkLogResponse.from(savedWorkLog)
    }
    
    override fun getCurrentOngoingWorkLog(workerId: UUID): WorkLogResponse? {
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        
        val workLog = workLogRepository.findCurrentOngoingWorkLog(worker)
        return workLog?.let { WorkLogResponse.from(it) }
    }
}
