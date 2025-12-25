package com.worktime.application.service

import com.worktime.adapter.input.web.dto.payroll.PayrollResponse
import com.worktime.adapter.input.web.dto.payroll.WageDetail
import com.worktime.adapter.input.web.dto.summary.DailySummary
import com.worktime.adapter.input.web.dto.summary.PeriodInfo
import com.worktime.adapter.input.web.dto.summary.WorkSummaryResponse
import com.worktime.adapter.input.web.dto.worklog.WorkLogResponse
import com.worktime.application.port.input.PayrollUseCase
import com.worktime.application.port.input.WageContractUseCase
import com.worktime.application.port.output.OwnerRepository
import com.worktime.application.port.output.WorkLogRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.exception.BusinessException
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.model.WorkLog
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

/**
 * Service implementing Payroll and Work Summary business logic.
 */
@Service
@Transactional(readOnly = true)
class PayrollService(
    private val workLogRepository: WorkLogRepository,
    private val workerRepository: WorkerRepository,
    private val ownerRepository: OwnerRepository,
    private val wageContractUseCase: WageContractUseCase
) : PayrollUseCase {
    
    override fun getWorkSummary(
        workerId: UUID,
        from: LocalDate,
        to: LocalDate
    ): WorkSummaryResponse {
        validateDateRange(from, to)
        
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        
        // Convert LocalDate to OffsetDateTime for query
        val fromDateTime = from.atStartOfDay().atOffset(ZoneOffset.UTC)
        val toDateTime = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
        
        // Find completed work logs in range
        val workLogs = workLogRepository.findCompletedWorkLogsInRange(
            worker = worker,
            startTime = fromDateTime,
            endTime = toDateTime
        )
        
        // Calculate total minutes
        val totalMinutes = workLogs.sumOf { workLog ->
            workLog.calculateDuration()?.toMinutes() ?: 0L
        }
        
        // Group by date for daily summaries
        val dailySummaries = workLogs
            .groupBy { workLog ->
                workLog.clockInAt.toLocalDate()
            }
            .map { (date, logs) ->
                val dailyMinutes = logs.sumOf { it.calculateDuration()?.toMinutes() ?: 0L }
                DailySummary(
                    date = date,
                    totalMinutes = dailyMinutes,
                    workLogCount = logs.size
                )
            }
            .sortedBy { it.date }
        
        val workLogResponses = workLogs.map { WorkLogResponse.from(it) }
        
        return WorkSummaryResponse(
            workerId = worker.id,
            workerName = worker.name,
            period = PeriodInfo(from, to),
            totalHours = totalMinutes / 60,
            totalMinutes = totalMinutes,
            workLogCount = workLogs.size,
            workLogs = workLogResponses,
            dailySummaries = dailySummaries
        )
    }
    
    override fun calculatePayroll(
        workerId: UUID,
        from: LocalDate,
        to: LocalDate
    ): PayrollResponse {
        validateDateRange(from, to)
        
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        
        // Convert LocalDate to OffsetDateTime for query
        val fromDateTime = from.atStartOfDay().atOffset(ZoneOffset.UTC)
        val toDateTime = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
        
        // Find completed work logs in range
        val workLogs = workLogRepository.findCompletedWorkLogsInRange(
            worker = worker,
            startTime = fromDateTime,
            endTime = toDateTime
        )
        
        // Calculate wage details for each work log
        val wageDetails = workLogs.map { workLog ->
            calculateWageDetailForWorkLog(workLog)
        }
        
        // Calculate totals
        val totalPay = wageDetails.sumOf { it.pay }
        val totalHours = wageDetails.sumOf { it.hours }
        
        return PayrollResponse(
            workerId = worker.id,
            workerName = worker.name,
            period = PeriodInfo(from, to),
            totalPay = totalPay,
            totalHours = totalHours,
            wageDetails = wageDetails
        )    }
    
    override fun getMonthlyPayrollSummary(
        userId: UUID,
        year: Int,
        month: Int,
        workerId: UUID?
    ): List<PayrollResponse> {
        // Validate year and month
        if (year < 1900 || year > 2100) {
            throw BusinessException("Invalid year: $year")
        }
        if (month < 1 || month > 12) {
            throw BusinessException("Invalid month: $month")
        }
        
        // Get owner by userId
        val owner = ownerRepository.findByUserId(userId)
            ?: throw ResourceNotFoundException("Owner not found for userId: $userId")
        
        // Calculate date range for the month
        val from = LocalDate.of(year, month, 1)
        val to = from.plusMonths(1).minusDays(1)
        
        // Get workers to calculate payroll for (filter by owner)
        val workers = if (workerId != null) {
            val worker = workerRepository.findById(workerId)
                ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
            // Verify the worker belongs to this owner
            if (worker.owner.id != owner.id) {
                throw BusinessException("Worker does not belong to this owner")
            }
            listOf(worker)
        } else {
            workerRepository.findByOwner(owner.id)
        }
        
        // Calculate payroll for each worker
        return workers.map { worker ->
            calculatePayroll(worker.id, from, to)
        }
    }
    
    override fun getWorkerMonthlyPayrollDetail(
        workerId: UUID,
        year: Int,
        month: Int
    ): com.worktime.adapter.input.web.dto.payroll.WorkerPayrollDetailResponse {
        // Validate year and month
        if (year < 1900 || year > 2100) {
            throw BusinessException("Invalid year: $year")
        }
        if (month < 1 || month > 12) {
            throw BusinessException("Invalid month: $month")
        }
        
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        
        // Calculate date range for the month
        val from = LocalDate.of(year, month, 1)
        val to = from.plusMonths(1).minusDays(1)
        
        // Convert LocalDate to OffsetDateTime for query
        val fromDateTime = from.atStartOfDay().atOffset(ZoneOffset.UTC)
        val toDateTime = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
        
        // Find completed work logs in range
        val workLogs = workLogRepository.findCompletedWorkLogsInRange(
            worker = worker,
            startTime = fromDateTime,
            endTime = toDateTime
        )
        
        // Calculate daily details
        val dailyDetails = workLogs
            .groupBy { workLog -> workLog.clockInAt.toLocalDate() }
            .map { (date, logs) ->
                val dailyMinutes = logs.sumOf { it.calculateDuration()?.toMinutes() ?: 0L }
                val dailyHours = dailyMinutes / 60.0
                
                // Get wage contract for the date
                val wageInfo = wageContractUseCase.getWageOnDate(workerId, date)
                val hourlyWage = wageInfo.hourlyWage ?: BigDecimal.ZERO
                val amount = hourlyWage.multiply(BigDecimal.valueOf(dailyHours))
                    .setScale(0, RoundingMode.HALF_UP)
                
                com.worktime.adapter.input.web.dto.payroll.DailyPayrollDetail(
                    date = date.toString(),
                    workMinutes = dailyMinutes,
                    workHours = dailyHours,
                    hourlyWage = hourlyWage,
                    amount = amount
                )
            }
            .sortedBy { it.date }
        
        // Calculate totals
        val totalWorkMinutes = dailyDetails.sumOf { it.workMinutes }
        val totalWorkHours = totalWorkMinutes / 60.0
        val totalAmount = dailyDetails.sumOf { it.amount }
        
        return com.worktime.adapter.input.web.dto.payroll.WorkerPayrollDetailResponse(
            workerId = worker.id,
            workerName = worker.name,
            year = year,
            month = month,
            totalWorkMinutes = totalWorkMinutes,
            totalWorkHours = totalWorkHours,
            totalAmount = totalAmount,
            dailyDetails = dailyDetails
        )
    }
    
    /**
     * Calculate wage detail for a single work log
     */
    private fun calculateWageDetailForWorkLog(workLog: WorkLog): WageDetail {
        val duration = workLog.calculateDuration()
            ?: throw BusinessException("Cannot calculate pay for incomplete work log: ${workLog.id}")
        
        val hours = duration.toMinutes() / 60.0
        val date = workLog.clockInAt.toLocalDate()
        
        // Get wage contract for the work date
        val wageInfo = wageContractUseCase.getWageOnDate(workLog.worker.id, date)
        
        val hourlyWage = wageInfo.hourlyWage
        val pay = if (hourlyWage != null) {
            hourlyWage.multiply(BigDecimal.valueOf(hours))
                .setScale(0, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
        
        return WageDetail(
            workLogId = workLog.id,
            date = date,
            hours = hours,
            hourlyWage = hourlyWage,
            pay = pay
        )
    }
    
    /**
     * Validate date range
     */
    private fun validateDateRange(from: LocalDate, to: LocalDate) {
        if (from.isAfter(to)) {
            throw BusinessException("Start date must be before or equal to end date (from: $from, to: $to)")
        }
    }
}

