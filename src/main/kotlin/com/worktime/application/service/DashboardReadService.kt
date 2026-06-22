package com.worktime.application.service

import com.worktime.adapter.input.web.dto.dashboard.DashboardSummaryResponse
import com.worktime.adapter.input.web.dto.dashboard.MonthKpiDto
import com.worktime.adapter.input.web.dto.dashboard.TodayKpiDto
import com.worktime.application.port.input.DashboardReadUseCase
import com.worktime.application.port.input.dto.*
import com.worktime.application.port.output.ScheduleRepository
import com.worktime.application.port.output.WorkLogRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.model.DashboardActionType
import com.worktime.domain.model.Schedule
import com.worktime.domain.model.WorkLog
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
        val actionNeededCount = calculateActionNeededCount(ownerId)
        
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
    
    override fun getDashboardActions(
        ownerId: UUID,
        date: LocalDate?,
        limit: Int,
        types: List<DashboardActionType>?
    ): DashboardActionListResponse {
        val targetDate = date ?: LocalDate.now()

        val allActions = mutableListOf<ActionItem>()

        // 1. PENDING_SCHEDULE_APPROVAL
        if (shouldInclude(types, DashboardActionType.PENDING_SCHEDULE_APPROVAL)) {
            val pendingSchedules = scheduleRepository.findByOwnerAndStatusIn(
                ownerId,
                listOf(
                    com.worktime.domain.model.ScheduleStatus.REQUESTED_BY_WORKER,
                    com.worktime.domain.model.ScheduleStatus.PROPOSED_BY_OWNER
                )
            )
            allActions.addAll(pendingSchedules.map { createPendingApprovalAction(it) })
        }



        val finalSorted = allActions.sortedWith(
            Comparator { o1, o2 ->
                val p = o2.priority.compareTo(o1.priority)
                if (p != 0) return@Comparator p
                
                val c = o1.createdAt.compareTo(o2.createdAt)
                if (c != 0) return@Comparator c
                
                o1.meta.workerName.compareTo(o2.meta.workerName)
            }
        )

        val limitedItems = finalSorted.take(limit)

        return DashboardActionListResponse(
            date = targetDate.toString(),
            timezone = "Asia/Seoul",
            totalCount = allActions.size,
            items = limitedItems
        )
    }

    override fun getAttendanceSummary(
        ownerId: UUID, 
        date: LocalDate, 
        includeLogWithoutSchedule: Boolean
    ): com.worktime.adapter.input.web.dto.dashboard.AttendanceSummaryResponse {
        // 1. Date Range & Fetch Schedules
        val dateRange = DashboardDateRange.todayRange(date)
        
        // Active Schedules: Include pending approvals to match TodayKpi logic
        val allSchedules = scheduleRepository.findByOwnerAndStatusIn(
            ownerId,
            listOf(
                com.worktime.domain.model.ScheduleStatus.REQUESTED_BY_WORKER,
                com.worktime.domain.model.ScheduleStatus.PROPOSED_BY_OWNER,
                com.worktime.domain.model.ScheduleStatus.CONFIRMED
            )
        )
        
        // Filter by Date Range (Overlap logic)
        val targetSchedules = allSchedules.filter { 
            it.startAtUtc.isBefore(dateRange.endExclusive) && it.endAtUtc.isAfter(dateRange.start)
        }
        
        // 2. Fetch WorkLogs (Batch)
        val scheduleIds = targetSchedules.map { it.id }
        val relatedWorkLogs = workLogRepository.findByScheduleIds(scheduleIds)
        val workLogsMap = relatedWorkLogs.groupBy { it.schedule?.id }
        
        // 3. Calculate Counts & Exceptions
        var scheduledCount = 0
        var checkedInCount = 0
        var notCheckedInCount = 0
        var notCheckedOutCount = 0
        
        val exceptions = mutableListOf<com.worktime.adapter.input.web.dto.dashboard.AttendanceExceptionDto>()
        
        // Sort schedules by start time for consistent exception list
        val sortedSchedules = targetSchedules.sortedBy { it.startAtUtc }
        
        for (schedule in sortedSchedules) {
            scheduledCount++
            
            val logs = workLogsMap[schedule.id]
            val hasCheckIn = !logs.isNullOrEmpty()
            
            if (hasCheckIn) {
                checkedInCount++
                val ongoingLog = logs!!.find { it.clockOutAt == null }
                if (ongoingLog != null) {
                    notCheckedOutCount++
                    
                    exceptions.add(com.worktime.adapter.input.web.dto.dashboard.AttendanceExceptionDto(
                        type = com.worktime.adapter.input.web.dto.dashboard.AttendanceExceptionType.CHECKIN_EXISTS_BUT_NO_CHECKOUT,
                        scheduleId = schedule.id,
                        workLogId = ongoingLog.id,
                        workerId = schedule.worker.id,
                        workerName = schedule.worker.name,
                        scheduledStartAt = schedule.startAtUtc,
                        scheduledEndAt = schedule.endAtUtc,
                        checkedInAt = ongoingLog.clockInAt,
                        checkedOutAt = null
                    ))
                }
            } else {
                notCheckedInCount++
                
                exceptions.add(com.worktime.adapter.input.web.dto.dashboard.AttendanceExceptionDto(
                    type = com.worktime.adapter.input.web.dto.dashboard.AttendanceExceptionType.SCHEDULE_EXISTS_BUT_NO_CHECKIN,
                    scheduleId = schedule.id,
                    workerId = schedule.worker.id,
                    workerName = schedule.worker.name,
                    scheduledStartAt = schedule.startAtUtc,
                    scheduledEndAt = schedule.endAtUtc,
                    checkedInAt = null,
                    checkedOutAt = null
                ))
            }
        }
        
        // 4. Construct Response
        return com.worktime.adapter.input.web.dto.dashboard.AttendanceSummaryResponse(
            date = date,
            summary = com.worktime.adapter.input.web.dto.dashboard.AttendanceCounts(
                scheduledCount = scheduledCount,
                checkedInCount = checkedInCount,
                notCheckedInCount = notCheckedInCount,
                notCheckedOutCount = notCheckedOutCount
            ),
            exceptions = exceptions
        )
    }

    private fun calculateActionNeededCount(ownerId: UUID): Int {
        // 1. Pending Schedules
        val pendingSchedules = scheduleRepository.findByOwnerAndStatusIn(
            ownerId, 
            listOf(
                com.worktime.domain.model.ScheduleStatus.REQUESTED_BY_WORKER,
                com.worktime.domain.model.ScheduleStatus.PROPOSED_BY_OWNER
            )
        )
        
        return pendingSchedules.size
    }

    private fun shouldInclude(types: List<DashboardActionType>?, type: DashboardActionType): Boolean {
        return types == null || types.contains(type)
    }

    private fun createPendingApprovalAction(schedule: Schedule): ActionItem {
        return ActionItem(
            id = "action:pending-approval:schedule:${schedule.id}",
            type = DashboardActionType.PENDING_SCHEDULE_APPROVAL,
            priority = 10,
            title = "스케줄 승인 대기",
            message = "${schedule.worker.name} ${formatTime(schedule.startAtUtc)}~${formatTime(schedule.endAtUtc)} 승인 필요",
            createdAt = schedule.createdAt.atOffset(java.time.ZoneOffset.UTC),
            target = ActionTarget(
                targetType = ActionTargetType.SCHEDULE,
                targetId = schedule.id.toString(),
                deepLink = "/schedule"
            ),
            meta = ActionMeta(
                workerId = schedule.worker.id.toString(),
                workerName = schedule.worker.name,
                scheduledStartAt = schedule.startAtUtc,
                scheduledEndAt = schedule.endAtUtc
            )
        )
    }



    private fun formatTime(odt: OffsetDateTime): String {
        val zdt = odt.atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul"))
        return "${zdt.hour.toString().padStart(2, '0')}:${zdt.minute.toString().padStart(2, '0')}"
    }

    override fun getTopWorkers(
        ownerId: UUID,
        from: LocalDate?,
        to: LocalDate?,
        limit: Int,
        sort: TopWorkerSort
    ): TopWorkersResponse {
        val zoneId = java.time.ZoneId.of("Asia/Seoul")
        val targetTo = to ?: LocalDate.now(zoneId)
        // Default to last 7 days (including today if to is today)
        // Rule: from (inclusive) ~ to (exclusive for OffsetDateTime conversion usually, but API param says inclusive date)
        // user requirement: 12/09 ~ 12/15 -> inclusive.
        // So conversion to OffsetDateTime: 12/09 00:00 <= t < 12/16 00:00
        val targetFrom = from ?: targetTo.minusDays(6)
        
        val startDateTime = targetFrom.atStartOfDay(zoneId).toOffsetDateTime()
        val endDateTime = targetTo.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime()

        // 1. Fetch Completed worklogs
        val workLogs = workLogRepository.findCompletedWorkLogsByOwnerAndTimeRange(
            ownerId, startDateTime, endDateTime
        )
        
        if (workLogs.isEmpty()) {
            return TopWorkersResponse(
                timezone = zoneId.id,
                range = DateRangeDto(
                    from = targetFrom,
                    to = targetTo,
                    days = java.time.temporal.ChronoUnit.DAYS.between(targetFrom, targetTo) + 1
                ),
                items = emptyList()
            )
        }
        
        // 2. Fetch Contracts (Batch)
        val distinctWorkers = workLogs.map { it.worker }.distinctBy { it.id }
        val contracts = wageContractRepository.findByWorkerIn(distinctWorkers)
        val contractsMap = contracts.groupBy { it.worker.id }
        
        // 3. Group and Calculate Stats
        val groupedLogs = workLogs.groupBy { it.worker.id }
        
        val items = groupedLogs.mapNotNull { (workerId, logs) ->
            if (logs.isEmpty()) return@mapNotNull null
            
            val worker = logs.first().worker
            
            var totalMinutes = 0L
            var totalWage = java.math.BigDecimal.ZERO
            val shifts = logs.size
            
            logs.forEach { log ->
                 val duration = log.calculateDuration()?.toMinutes() ?: 0L
                 totalMinutes += duration
                 
                 // Wage Calc
                 val workDate = log.clockInAt.atZoneSameInstant(zoneId).toLocalDate()
                 val validContract = contractsMap[workerId]?.find { it.isActiveOn(workDate) }
                 
                 if (validContract != null) {
                     // MVP Policy: floor(minutes / 60 * wage) -> Not accurate double math.
                     // Suggestion: minutes * hourly / 60 to preserve precision then floor.
                     // user prompt: "sum( floor(totalWorkMinutes / 60 * hourlyWage) )" -> Check interpretation.
                     // Usually it implies: for each shift, calculate wage.
                     // Formula: (minutes * wage) / 60. floor result.
                     val wageForShift = java.math.BigDecimal.valueOf(duration)
                         .multiply(validContract.hourlyWage)
                         .divide(java.math.BigDecimal.valueOf(60), 0, java.math.RoundingMode.FLOOR)
                     totalWage = totalWage.add(wageForShift)
                 }
            }
            
            TopWorkerItem(
                rank = 0, // Fill later
                worker = WorkerInfo(
                    workerId = worker.id.toString(),
                    name = worker.name,
                    pictureUrl = null // TODO
                ),
                stats = WorkerStats(
                    completedShifts = shifts,
                    totalWorkMinutes = totalMinutes,
                    accruedWage = totalWage
                )
            )
        }
        
        // 4. Sort and Rank
        val sortedItems = when (sort) {
            TopWorkerSort.WAGE_DESC -> items.sortedWith(
                compareByDescending<TopWorkerItem> { it.stats.accruedWage }
                    .thenBy { it.worker.name }
            )
            TopWorkerSort.MINUTES_DESC -> items.sortedWith(
                compareByDescending<TopWorkerItem> { it.stats.totalWorkMinutes }
                    .thenBy { it.worker.name }
            )
            TopWorkerSort.SHIFTS_DESC -> items.sortedWith(
                compareByDescending<TopWorkerItem> { it.stats.completedShifts }
                    .thenBy { it.worker.name }
            )
        }
        
        // 5. Limit and Badge
        val limitedItems = sortedItems.take(limit)
        
        val rankedItems = limitedItems.mapIndexed { index, item ->
            val badges = mutableListOf<WorkerBadge>()
            if (index == 0 && sortedItems.isNotEmpty()) {
                if (sort == TopWorkerSort.WAGE_DESC) badges.add(WorkerBadge.TOP_WAGE)
                if (sort == TopWorkerSort.MINUTES_DESC) badges.add(WorkerBadge.TOP_MINUTES)
            }
            
            item.copy(
                rank = index + 1,
                badges = badges
            )
        }

        return TopWorkersResponse(
            timezone = zoneId.id,
            range = DateRangeDto(
                from = targetFrom,
                to = targetTo,
                days = java.time.temporal.ChronoUnit.DAYS.between(targetFrom, targetTo) + 1
            ),
            items = rankedItems
        )
    }
}
