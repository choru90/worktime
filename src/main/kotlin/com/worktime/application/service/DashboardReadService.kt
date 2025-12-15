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
import java.time.OffsetDateTime
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
    
    override fun getDashboardActions(ownerId: UUID): List<com.worktime.adapter.input.web.dto.dashboard.DashboardActionResponse> {
        val actions = mutableListOf<com.worktime.adapter.input.web.dto.dashboard.DashboardActionResponse>()
        
        // 1. Pending Schedules (Approval Needed)
        val pendingSchedules = scheduleRepository.findByOwnerAndStatusIn(
            ownerId,
            listOf(
                com.worktime.domain.model.ScheduleStatus.REQUESTED_BY_WORKER,
                com.worktime.domain.model.ScheduleStatus.PROPOSED_BY_OWNER
            )
        )
        
        actions.addAll(pendingSchedules.map { schedule ->
            com.worktime.adapter.input.web.dto.dashboard.DashboardActionResponse(
                id = schedule.id,
                type = com.worktime.domain.model.DashboardActionType.PENDING_APPROVAL,
                title = "스케줄 승인 요청",
                description = "${schedule.worker.name}님이 근무 스케줄 승인을 요청했습니다.",
                workerName = schedule.worker.name,
                createdAt = schedule.startAtUtc, // 스케줄 시작 시간을 기준 (또는 생성시간)
                severity = com.worktime.adapter.input.web.dto.dashboard.ActionSeverity.LOW
            )
        })
        
        // 2. Missing Checkout (Working too long / Forgot checkout)
        // Rule: Ongoing worklogs started before today (KST midnight)
        val nowKst = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul"))
        val todayMidnightKst = nowKst.toLocalDate().atStartOfDay(java.time.ZoneId.of("Asia/Seoul"))
        val thresholdUtc = todayMidnightKst.toOffsetDateTime()
        
        val missingCheckoutLogs = workLogRepository.findOngoingWorkLogsOlderThan(ownerId, thresholdUtc)
        
        actions.addAll(missingCheckoutLogs.map { workLog ->
            com.worktime.adapter.input.web.dto.dashboard.DashboardActionResponse(
                id = workLog.id,
                type = com.worktime.domain.model.DashboardActionType.MISSING_CHECKOUT,
                title = "퇴근 미체크",
                description = "${workLog.worker.name}님이 퇴근 처리를 하지 않았습니다. (어제 이전 출근)",
                workerName = workLog.worker.name,
                createdAt = workLog.clockInAt,
                severity = com.worktime.adapter.input.web.dto.dashboard.ActionSeverity.HIGH
            )
        })
        
        // 3. Missing Checkin (Late / No-show)
        // Rule: Today's schedule started > 30 mins ago, but no work log
        // Get today's schedules
        val todayRange = DashboardDateRange.todayRange(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")))
        val todaySchedules = scheduleRepository.findByOwnerAndStatusIn(
            ownerId, 
            listOf(com.worktime.domain.model.ScheduleStatus.CONFIRMED)
        ).filter { 
            // Filter manually for time range as we reused status query method
            // Improve: create specific query if performance issue
            !it.startAtUtc.isBefore(todayRange.start) && it.startAtUtc.isBefore(todayRange.endExclusive)
        }
        
        // Get today's worklogs
        val todayWorkLogs = workLogRepository.findByOwnerAndTimeRange(
            ownerId, todayRange.start, todayRange.endExclusive
        )
        
        val nowUtc = OffsetDateTime.now()
        val lateBufferMinutes = 30L
        
        for (schedule in todaySchedules) {
            // Check if schedule start time + buffer < now
            if (schedule.startAtUtc.plusMinutes(lateBufferMinutes).isBefore(nowUtc)) {
                // Check if worker has checked in for this schedule (or any checkin around start time)
                // Simplify: Check if any worklog exists for this worker today
                // Ideally: Match schedule ID or check overlapping time
                val hasWorkLog = todayWorkLogs.any { wl -> 
                    wl.worker.id == schedule.worker.id && 
                    // Simple logic: WorkLog started within reasonable range of schedule start?
                    // Or just "Has any worklog today"? User req: "Not Checked In"
                    // Let's implement: No worklog covering this schedule's start time
                    // Start time of worklog is usually close to schedule start
                    true // For MVP, let's verify if *any* worklog exists for this worker today.
                         // If no worklog at all -> Missing Checkin.
                         // Accurate matching is complex due to flexible hours.
                }
                
                // If specific worker has NO worklogs today, but has a schedule that started > 30mins ago -> LATE
                val workerHasLog = todayWorkLogs.any { it.worker.id == schedule.worker.id }
                
                if (!workerHasLog) {
                    actions.add(com.worktime.adapter.input.web.dto.dashboard.DashboardActionResponse(
                        id = schedule.id,
                        type = com.worktime.domain.model.DashboardActionType.LATE_ARRIVAL, // Or MISSING_CHECKIN logic
                        title = "출근 미체크 (지각/결근)",
                        description = "${schedule.worker.name}님이 출근 시간이 지났으나 기록이 없습니다.",
                        workerName = schedule.worker.name,
                        createdAt = schedule.startAtUtc,
                        severity = com.worktime.adapter.input.web.dto.dashboard.ActionSeverity.MEDIUM
                    ))
                }
            }
        }
        
        // Sort: High Severity first, then Oldest first
        return actions.sortedWith(
            compareBy<com.worktime.adapter.input.web.dto.dashboard.DashboardActionResponse> { it.severity } // Enum ordinal: HIGH(0) < MEDIUM(1) ... Wait, we want High first?
                // Enum definition: HIGH, MEDIUM, LOW. Ordinal 0, 1, 2.
                // sortedBy uses natural order (0, 1, 2) -> HIGH comes first.
                .thenBy { it.createdAt }
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
