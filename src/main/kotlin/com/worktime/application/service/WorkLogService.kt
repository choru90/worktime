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
    private fun lockLog(id: UUID): WorkLog {
        val log=workLogRepository.findById(id) ?: throw ResourceNotFoundException("Resource not found")
        workerRepository.lockForWrite(log.worker)
        workLogRepository.refreshForWrite(log)
        return log
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    override fun correctWorkLog(workerId: UUID,id: UUID,request: com.worktime.adapter.input.web.dto.worklog.CorrectWorkLogRequest): WorkLogResponse {
        val initial=workLogRepository.findById(id) ?: throw ResourceNotFoundException("Resource not found")
        if(initial.worker.id!=workerId) throw ResourceNotFoundException("Resource not found")
        val log=lockLog(id)
        if(log.correctionCount!=request.expectedCorrectionCount || log.status!=request.expectedStatus) throw WorkLogConflictException("기록이 변경되었습니다. 다시 조회해주세요.")
        if(log.status !in listOf(WorkLogStatus.ONGOING,WorkLogStatus.COMPLETED,WorkLogStatus.REJECTED)) throw InvalidWorkLogStateException("승인된 기록은 정정할 수 없습니다")
        if(!request.clockOutAt.isAfter(request.clockInAt) || request.clockOutAt.isAfter(OffsetDateTime.now())) throw InvalidWorkLogStateException("퇴근은 출근 이후이며 미래 시간이 아니어야 합니다")
        if(workLogRepository.hasOverlap(log.worker,id,request.clockInAt,request.clockOutAt)) throw WorkLogConflictException("다른 근무 기록과 시간이 겹칩니다")
        log.corrections.add(com.worktime.domain.model.WorkLogCorrection(workLog=log,previousIn=log.clockInAt,previousOut=log.clockOutAt,previousStatus=log.status,requestedIn=request.clockInAt,requestedOut=request.clockOutAt,reason=request.reason.trim(),previousRejectionReason=log.rejectionReason,revision=log.correctionCount+1))
        log.clockInAt=request.clockInAt;log.clockOutAt=request.clockOutAt;log.status=WorkLogStatus.COMPLETED
        log.rejectionReason=null;log.correctionCount++;log.correctionReason=request.reason.trim()
        return WorkLogResponse.from(workLogRepository.save(log))
    }

    
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    override fun clockIn(workerId: UUID, request: ClockInRequest): WorkLogResponse {
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        workerRepository.lockForWrite(worker)
        
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
        
        if (schedule != null && schedule.worker.id != workerId) {
            throw ResourceNotFoundException("Resource not found")
        }
        if (schedule != null && workLogRepository.findByScheduleIds(listOf(schedule.id)).isNotEmpty()) {
            throw WorkLogConflictException("A work log already exists for this schedule")
        }
        val workLog = WorkLog(
            worker = worker,
            schedule = schedule,
            clockInAt = request.clockInAt
        )
        
        val savedWorkLog = workLogRepository.save(workLog)
        return WorkLogResponse.from(savedWorkLog)
    }
    
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    override fun clockOut(workerId: UUID, request: ClockOutRequest): WorkLogResponse {
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        workerRepository.lockForWrite(worker)
        
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
    
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    override fun createManualWorkLog(workerId: UUID, scheduleId: UUID, request: ManualWorkLogRequest): WorkLogResponse {
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        workerRepository.lockForWrite(worker)
        
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
        
        if (workLogRepository.findByScheduleIds(listOf(scheduleId)).isNotEmpty()) {
            throw WorkLogConflictException("A work log already exists for this schedule")
        }
        
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
        status: WorkLogStatus?,
        ownerId: UUID
    ): List<WorkLogResponse> {
        // Filter by workerId if provided
        val workLogList = when {
            workerId != null -> {
                val worker = workerRepository.findById(workerId)
                    ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
                if (worker.owner.id != ownerId) throw ResourceNotFoundException("Resource not found")
                
                if (from != null && to != null) {
                    workLogRepository.findByWorkerAndTimeRange(worker, from, to)
                } else {
                    emptyList()
                }
            }
            from != null && to != null -> {
                workLogRepository.findByOwnerAndTimeRange(ownerId, from, to)
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
                scheduleRepository.findByOwnerAndTimeRange(ownerId, from, to)
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
    
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    override fun confirmWorkLog(workLogId: UUID, expectedCorrectionCount: Int): WorkLogResponse {
        val workLog = lockLog(workLogId)
        if(workLog.correctionCount!=expectedCorrectionCount)throw WorkLogConflictException("정정된 기록입니다. 다시 조회한 후 검토해주세요.")
        
        try {
            workLog.confirm()
        } catch (e: IllegalArgumentException) {
            throw InvalidWorkLogStateException(e.message ?: "Cannot confirm work log in current state")
        }
        
        val savedWorkLog = workLogRepository.save(workLog)
        return WorkLogResponse.from(savedWorkLog)
    }
    
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    override fun rejectWorkLog(workLogId: UUID, reason: String?, expectedCorrectionCount: Int): WorkLogResponse {
        val workLog = lockLog(workLogId)
        if(workLog.correctionCount!=expectedCorrectionCount)throw WorkLogConflictException("정정된 기록입니다. 다시 조회한 후 검토해주세요.")
        
        try {
            workLog.reject()
            workLog.rejectionReason = reason?.trim()?.take(500)
        } catch (e: IllegalArgumentException) {
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


