package com.worktime.application.service

import com.worktime.adapter.input.web.dto.schedule.ScheduleCreateRequest
import com.worktime.adapter.input.web.dto.schedule.ScheduleRequestType
import com.worktime.adapter.input.web.dto.schedule.ScheduleResponse
import com.worktime.adapter.input.web.dto.schedule.ScheduleUpdateRequest
import com.worktime.application.port.input.ScheduleUseCase
import com.worktime.application.port.output.OwnerRepository
import com.worktime.application.port.output.ScheduleRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.exception.BusinessException
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
    private val workerRepository: WorkerRepository,
    private val ownerRepository: OwnerRepository
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

    override fun updateScheduleTime(scheduleId: UUID, request: ScheduleUpdateRequest): ScheduleResponse {
        val schedule = scheduleRepository.findById(scheduleId)
            ?: throw ResourceNotFoundException("Schedule not found with id: $scheduleId")

        // 시간 유효성 검사
        if (!request.endAtUtc.isAfter(request.startAtUtc)) {
             throw IllegalArgumentException("End time must be after start time")
        }

        // 중복 체크 (자신 제외)
        val overlappingSchedules = scheduleRepository.findConfirmedSchedulesOverlapping(
            schedule.worker, request.startAtUtc, request.endAtUtc
        ).filter { it.id != scheduleId }

        if (overlappingSchedules.isNotEmpty()) {
            throw ScheduleConflictException(
                "Schedule conflicts with existing confirmed schedule(s). " +
                "Conflicting schedules: " +
                overlappingSchedules.joinToString(", ") { 
                    "${it.startAtUtc} ~ ${it.endAtUtc}" 
                }
            )
        }

        // 업데이트
        schedule.updateTime(request.startAtUtc, request.endAtUtc, request.timezone)
        
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
    
    override fun getAllSchedules(
        userId: UUID,
        from: OffsetDateTime?,
        to: OffsetDateTime?,
        workerId: UUID?,
        status: ScheduleStatus?
    ): List<ScheduleResponse> {
        // Get owner from userId
        val owner = ownerRepository.findByUserId(userId)
            ?: throw ResourceNotFoundException("Owner not found for userId: $userId")
        
        // Filter by workerId if provided, otherwise get all schedules for owner
        val schedules = when {
            workerId != null -> {
                val worker = workerRepository.findById(workerId)
                    ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
                // Verify worker belongs to this owner
                if (worker.owner.id != owner.id) {
                    throw BusinessException("Worker does not belong to this owner")
                }
                if (from != null && to != null) {
                    scheduleRepository.findByWorkerAndTimeRange(worker, from, to)
                } else {
                    emptyList()
                }
            }
            from != null && to != null -> {
                scheduleRepository.findByOwnerAndTimeRange(owner.id, from, to)
            }
            else -> emptyList()
        }
        
        // Filter by status if provided
        val filteredSchedules = if (status != null) {
            schedules.filter { it.status == status }
        } else {
            schedules
        }
        
        return filteredSchedules.map { ScheduleResponse.from(it) }
    }
    
    /**
     * 월별 스케줄 자동 생성
     * 고정 근무를 기반으로 해당 월의 모든 날짜에 개별 스케줄 생성
     */
    fun generateMonthlySchedules(
        storeId: UUID,
        year: Int,
        month: Int,
        regularShifts: List<com.worktime.domain.model.RegularShift>
    ): List<ScheduleResponse> {
        val startDate = java.time.LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)
        
        val createdSchedules = mutableListOf<Schedule>()
        var currentDate = startDate
        
        while (!currentDate.isAfter(endDate)) {
            // 해당 요일의 고정 근무 필터링
            val shiftsForDay = regularShifts.filter { shift ->
                shift.active && 
                shift.dayOfWeek == currentDate.dayOfWeek &&
                (shift.effectiveFrom == null || !shift.effectiveFrom!!.isAfter(currentDate)) &&
                (shift.effectiveUntil == null || !shift.effectiveUntil!!.isBefore(currentDate))
            }
            
            for (shift in shiftsForDay) {
                val worker = shift.worker
                val timezone = "Asia/Seoul"
                val zoneId = java.time.ZoneId.of(timezone)
                
                // LocalTime + LocalDate → OffsetDateTime 변환
                val startAtUtc = java.time.ZonedDateTime.of(
                    currentDate, shift.startTime, zoneId
                ).toOffsetDateTime().withOffsetSameInstant(java.time.ZoneOffset.UTC)
                
                val endAtUtc = java.time.ZonedDateTime.of(
                    currentDate, shift.endTime, zoneId
                ).toOffsetDateTime().withOffsetSameInstant(java.time.ZoneOffset.UTC)
                
                // 이미 동일 근무자 + 동일 시간에 스케줄이 있는지 확인
                val existingSchedules = scheduleRepository.findConfirmedSchedulesOverlapping(
                    worker = worker,
                    startTime = startAtUtc,
                    endTime = endAtUtc
                )
                
                if (existingSchedules.isEmpty()) {
                    val schedule = Schedule(
                        worker = worker,
                        startAtUtc = startAtUtc,
                        endAtUtc = endAtUtc,
                        timezone = timezone,
                        status = ScheduleStatus.CONFIRMED  // 바로 확정 상태로 생성
                    )
                    createdSchedules.add(scheduleRepository.save(schedule))
                }
            }
            
            currentDate = currentDate.plusDays(1)
        }
        
        return createdSchedules.map { ScheduleResponse.from(it) }
    }
}
