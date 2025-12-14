package com.worktime.application.service

import com.worktime.adapter.input.web.dto.worklog.ClockInRequest
import com.worktime.adapter.input.web.dto.worklog.ClockOutRequest
import com.worktime.adapter.input.web.dto.worklog.WorkLogResponse
import com.worktime.application.port.input.WorkLogUseCase
import com.worktime.application.port.output.ScheduleRepository
import com.worktime.application.port.output.WorkLogRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.exception.InvalidWorkLogStateException
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.exception.WorkLogConflictException
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
        val workLogs = when {
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
        
        // Filter by status if provided
        val filteredLogs = if (status != null) {
            workLogs.filter { it.status == status }
        } else {
            workLogs
        }
        
        return filteredLogs.map { WorkLogResponse.from(it) }
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
}
