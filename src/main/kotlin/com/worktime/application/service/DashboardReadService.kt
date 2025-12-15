package com.worktime.application.service

import com.worktime.adapter.input.web.dto.dashboard.DashboardSummaryResponse
import com.worktime.adapter.input.web.dto.dashboard.MonthKpiDto
import com.worktime.adapter.input.web.dto.dashboard.TodayKpiDto
import com.worktime.application.port.input.DashboardReadUseCase
import com.worktime.application.port.output.ScheduleRepository
import com.worktime.application.port.output.WorkLogRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.util.DashboardDateRange
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.*
import kotlin.math.max

@Service
@Transactional(readOnly = true)
class DashboardReadService(
    private val scheduleRepository: ScheduleRepository,
    private val workLogRepository: WorkLogRepository,
    private val workerRepository: WorkerRepository,
    private val wageContractRepository: com.worktime.application.port.output.WageContractRepository
) : DashboardReadUseCase {
    
    override fun getDashboardSummary(ownerId: UUID, date: LocalDate): DashboardSummaryResponse {
        val todayRange = DashboardDateRange.todayRange(date)
        val monthRange = DashboardDateRange.monthRange(date)
        
        // 1. Today KPI
        val todayKpi = calculateTodayKpi(ownerId, todayRange)
        
        // 2. Month KPI
        val monthKpi = calculateMonthKpi(ownerId, monthRange)
        
        // 3. Action needed count
        val actionNeededCount = calculateActionNeededCount(ownerId, todayKpi)
        
        return DashboardSummaryResponse(
            date = date.toString(),
            timezone = "Asia/Seoul",
            today = todayKpi,
            month = monthKpi,
            actionNeededCount = actionNeededCount
        )
    }
    
    private fun calculateTodayKpi(ownerId: UUID, range: com.worktime.domain.util.DateTimeRange): TodayKpiDto {
        // Scheduled count (excluding canceled/rejected)
        val scheduledCount = scheduleRepository.countByOwnerAndTimeRangeExcludingCanceledAndRejected(
            ownerId, range.start, range.endExclusive
        ).toInt()
        
        // WorkLogs for today
        val workLogs = workLogRepository.findByOwnerAndTimeRange(
            ownerId, range.start, range.endExclusive
        )
        
        // Checked in count (all worklogs started today)
        val checkedInCount = workLogs.size
        
        // Not checked in (Scheduled - CheckedIn, min 0)
        // Note: This is a simplified logic as per requirements. 
        // Ideally should match schedule <-> worklog, but requirements say "scheduledCount - checkedInCount" logic implication
        // Requirement: "notCheckedInCount는 스케줄은 있는데 출근 기록 없는 것으로 계산"
        // Simply: max(0, scheduled - checkedIn) for MVP
        val notCheckedInCount = maxOf(0, scheduledCount - checkedInCount)
        
        // Not checked out (Status ONGOING)
        val notCheckedOutCount = workLogs.count { it.status == com.worktime.domain.model.WorkLogStatus.ONGOING }
        
        // Total work minutes (sum of duration for completed logs)
        val totalWorkMinutes = workLogs
            .filter { it.clockOutAt != null }
            .sumOf { it.calculateDuration()?.toMinutes() ?: 0L }
            
        return TodayKpiDto(
            scheduledCount = scheduledCount,
            checkedInCount = checkedInCount,
            notCheckedInCount = notCheckedInCount,
            notCheckedOutCount = notCheckedOutCount,
            totalWorkMinutes = totalWorkMinutes
        )
    }
    
    private fun calculateMonthKpi(ownerId: UUID, range: com.worktime.domain.util.DateTimeRange): MonthKpiDto {
        // 1. Accrued Wage
        // Get all completed/confirmed worklogs for the month
        val workLogs = workLogRepository.findByOwnerAndTimeRange(
            ownerId, range.start, range.endExclusive
        ).filter { 
            it.status == com.worktime.domain.model.WorkLogStatus.COMPLETED || 
            it.status == com.worktime.domain.model.WorkLogStatus.CONFIRMED 
        }
        
        // Calculate wage for each worklog
        var totalAccruedWage = java.math.BigDecimal.ZERO
        
        // Optimization: Load all contracts for workers involved
        // But for MVP, we might just query per worklog or worker
        val workerIds = workLogs.map { it.worker.id }.distinct()
        // Map workerId -> List<WageContract>
        val contractsMap = workerIds.associateWith { workerId ->
             // We need to fetch worker entity first or use workerId if repository supports it
             // workLog.worker is fully loaded (Lazy?) - Accessing it might trigger query
             // Assuming lazy loading works or we fetch explicitly
             wageContractRepository.findByWorker(workLogs.first { wl -> wl.worker.id == workerId }.worker)
        }
        
        for (workLog in workLogs) {
            val workDate = workLog.clockInAt.atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toLocalDate()
            val contracts = contractsMap[workLog.worker.id] ?: emptyList()
            
            // Find valid contract for the work date
            val validContract = contracts.find { it.isActiveOn(workDate) }
            
            if (validContract != null) {
                // Calculate wage: hours * hourlyWage
                val hours = workLog.calculateDuration()?.toMinutes()?.toDouble()?.div(60.0) ?: 0.0
                val wage = validContract.hourlyWage.multiply(java.math.BigDecimal.valueOf(hours))
                totalAccruedWage = totalAccruedWage.add(wage)
            }
        }
        
        return MonthKpiDto(
            accruedWage = totalAccruedWage.setScale(0, java.math.RoundingMode.HALF_UP),
            forecastWage = java.math.BigDecimal.ZERO // TODO: Implement forecast logic
        )
    }
    
    private fun calculateActionNeededCount(ownerId: UUID, todayKpi: TodayKpiDto): Int {
        // 1. Pending Schedules
        val pendingSchedules = scheduleRepository.findByOwnerAndStatusIn(
            ownerId, 
            listOf(
                com.worktime.domain.model.ScheduleStatus.REQUESTED_BY_WORKER,
                com.worktime.domain.model.ScheduleStatus.PROPOSED_BY_OWNER
            )
        )
        
        val pendingCount = pendingSchedules.size
        
        // 2. Today's issues
        val missingCheckIn = todayKpi.notCheckedInCount
        val missingCheckOut = todayKpi.notCheckedOutCount
        
        return pendingCount + missingCheckIn + missingCheckOut
    }
}
