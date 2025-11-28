package com.worktime.application.service

import com.worktime.adapter.input.web.dto.schedule.ScheduleCreateRequest
import com.worktime.adapter.input.web.dto.schedule.ScheduleRequestType
import com.worktime.adapter.input.web.dto.schedule.ScheduleResponse
import com.worktime.application.port.input.ScheduleUseCase
import com.worktime.application.port.output.ScheduleRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.exception.InvalidScheduleStateException
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.exception.ScheduleConflictException
import com.worktime.domain.model.Schedule
import com.worktime.domain.model.ScheduleStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

/**
 * Service implementing Schedule business logic.
 */
@Service
@Transactional
class ScheduleService(
    private val scheduleRepository: ScheduleRepository,
    private val workerRepository: WorkerRepository
) : ScheduleUseCase {
    
    override fun createSchedule(request: ScheduleCreateRequest): ScheduleResponse {
        val worker = workerRepository.findById(request.workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: ${request.workerId}")
        
        // Check for time conflicts with CONFIRMED schedules
        val overlappingSchedules = scheduleRepository.findConfirmedSchedulesOverlapping(
            worker = worker,
            startTime = request.startAtUtc,
            endTime = request.endAtUtc
        )
        
        if (overlappingSchedules.isNotEmpty()) {
            throw ScheduleConflictException(
                "Schedule conflicts with existing confirmed schedule(s). " +
                "Conflicting schedules: " +
                overlappingSchedules.joinToString(", ") { 
                    "${it.startAtUtc} ~ ${it.endAtUtc}" 
                }
            )
        }
        
        // Determine initial status based on request type
        val status = when (request.requestType) {
            ScheduleRequestType.WORKER_REQUEST -> ScheduleStatus.REQUESTED_BY_WORKER
            ScheduleRequestType.OWNER_PROPOSAL -> ScheduleStatus.PROPOSED_BY_OWNER
        }
        
        val schedule = Schedule(
            worker = worker,
            startAtUtc = request.startAtUtc,
            endAtUtc = request.endAtUtc,
            timezone = request.timezone,
            status = status
        )
        
        val savedSchedule = scheduleRepository.save(schedule)
        return ScheduleResponse.from(savedSchedule)
    }
    
    override fun confirmSchedule(scheduleId: UUID): ScheduleResponse {
        val schedule = scheduleRepository.findById(scheduleId)
            ?: throw ResourceNotFoundException("Schedule not found with id: $scheduleId")
        
        try {
            schedule.confirm()
        } catch (e: IllegalArgumentException) {
            throw InvalidScheduleStateException(e.message ?: "Cannot confirm schedule in current state")
        }
        
        val savedSchedule = scheduleRepository.save(schedule)
        return ScheduleResponse.from(savedSchedule)
    }
    
    override fun rejectSchedule(scheduleId: UUID): ScheduleResponse {
        val schedule = scheduleRepository.findById(scheduleId)
            ?: throw ResourceNotFoundException("Schedule not found with id: $scheduleId")
        
        try {
            schedule.reject()
        } catch (e: IllegalArgumentException) {
            throw InvalidScheduleStateException(e.message ?: "Cannot reject schedule in current state")
        }
        
        val savedSchedule = scheduleRepository.save(schedule)
        return ScheduleResponse.from(savedSchedule)
    }
    
    override fun cancelSchedule(scheduleId: UUID): ScheduleResponse {
        val schedule = scheduleRepository.findById(scheduleId)
            ?: throw ResourceNotFoundException("Schedule not found with id: $scheduleId")
        
        try {
            schedule.cancel()
        } catch (e: IllegalArgumentException) {
            throw InvalidScheduleStateException(e.message ?: "Cannot cancel schedule in current state")
        }
        
        val savedSchedule = scheduleRepository.save(schedule)
        return ScheduleResponse.from(savedSchedule)
    }
    
    override fun getScheduleById(scheduleId: UUID): ScheduleResponse {
        val schedule = scheduleRepository.findById(scheduleId)
            ?: throw ResourceNotFoundException("Schedule not found with id: $scheduleId")
        return ScheduleResponse.from(schedule)
    }
    
    override fun getSchedulesByWorker(
        workerId: UUID,
        from: OffsetDateTime?,
        to: OffsetDateTime?
    ): List<ScheduleResponse> {
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        
        val schedules = if (from != null && to != null) {
            scheduleRepository.findByWorkerAndTimeRange(worker, from, to)
        } else {
            // If no date range specified, return empty list or implement a default range
            emptyList()
        }
        
        return schedules.map { ScheduleResponse.from(it) }
    }
}
