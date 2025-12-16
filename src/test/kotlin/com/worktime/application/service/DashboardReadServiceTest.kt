package com.worktime.application.service

import com.worktime.adapter.input.web.dto.dashboard.ActionSeverity
import com.worktime.domain.model.DashboardActionType
import com.worktime.application.port.output.ScheduleRepository
import com.worktime.application.port.output.WageContractRepository
import com.worktime.application.port.output.WorkLogRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.model.*
import com.worktime.domain.util.DashboardDateRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

@DisplayName("DashboardReadService 단위 테스트")
class DashboardReadServiceTest {

    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var workLogRepository: WorkLogRepository
    private lateinit var workerRepository: WorkerRepository
    private lateinit var wageContractRepository: WageContractRepository
    private lateinit var dashboardReadService: DashboardReadService

    private val ownerId = UUID.randomUUID()
    private val workerId = UUID.randomUUID()
    private val worker = Worker(id = workerId, name = "Test Worker", phoneNumber = "010-1234-5678", owner = User(id = ownerId, email = "o", password = "p", name = "n", role = UserRole.OWNER))
    
    // KST 2025-12-15
    private val targetDate = LocalDate.of(2025, 12, 15)
    private val todayRange = DashboardDateRange.todayRange(targetDate)
    private val monthRange = DashboardDateRange.monthRange(targetDate)

    @BeforeEach
    fun setUp() {
        scheduleRepository = mock()
        workLogRepository = mock()
        workerRepository = mock()
        wageContractRepository = mock()
        dashboardReadService = DashboardReadService(
            scheduleRepository, workLogRepository, workerRepository, wageContractRepository
        )
    }

    @Test
    @DisplayName("일정 0개: 모든 카운트가 0이어야 함")
    fun summary_noSchedules_shouldReturnZeros() {
        // Given
        whenever(scheduleRepository.countByOwnerAndTimeRangeExcludingCanceledAndRejected(any(), any(), any()))
            .thenReturn(0L)
        whenever(workLogRepository.findByOwnerAndTimeRange(any(), eq(todayRange.start), eq(todayRange.endExclusive)))
            .thenReturn(emptyList())
        whenever(workLogRepository.findByOwnerAndTimeRange(any(), eq(monthRange.start), eq(monthRange.endExclusive)))
            .thenReturn(emptyList())
        whenever(scheduleRepository.findByOwnerAndStatusIn(any(), any()))
            .thenReturn(emptyList())

        // When
        val result = dashboardReadService.getDashboardSummary(ownerId, targetDate)

        // Then
        assertEquals(0, result.today.scheduledCount)
        assertEquals(0, result.today.checkedInCount)
        assertEquals(0, result.today.notCheckedInCount)
        assertEquals(0, result.today.notCheckedOutCount)
        assertEquals(0, result.today.totalWorkMinutes)
        assertEquals(0, result.actionNeededCount)
    }

    @Test
    @DisplayName("오늘 일정 2개, WorkLog 1개(출근만): KPI가 정확해야 함")
    fun summary_schedulesAndOngoingWorkLog_shouldCalculateCorrectly() {
        // Given
        // 스케줄 2개
        whenever(scheduleRepository.countByOwnerAndTimeRangeExcludingCanceledAndRejected(any(), any(), any()))
            .thenReturn(2L)
        
        // 워크로그 1개 (ONGOING)
        val ongoingWorkLog = WorkLog(
            worker = worker,
            clockInAt = todayRange.start.plusHours(9), // 09:00 KST
            clockOutAt = null,
            status = WorkLogStatus.ONGOING
        )
        whenever(workLogRepository.findByOwnerAndTimeRange(any(), eq(todayRange.start), eq(todayRange.endExclusive)))
            .thenReturn(listOf(ongoingWorkLog))
        
        // 월 데이터 없음
        whenever(workLogRepository.findByOwnerAndTimeRange(any(), eq(monthRange.start), eq(monthRange.endExclusive)))
            .thenReturn(emptyList()) // 월 집계용 (오늘 것도 포함되어야 하지만 여기서는 로직 테스트이므로 별도 모킹 가능)
            
        // When
        val result = dashboardReadService.getDashboardSummary(ownerId, targetDate)

        // Then
        assertEquals(2, result.today.scheduledCount)
        assertEquals(1, result.today.checkedInCount)
        assertEquals(1, result.today.notCheckedInCount) // 2 - 1 = 1
        assertEquals(1, result.today.notCheckedOutCount) // ONGOING 1개
        assertEquals(0, result.today.totalWorkMinutes) // 퇴근 안했으므로 0분
    }

    @Test
    @DisplayName("시급 계약 변경 시 급여 계산이 정확해야 함")
    fun summary_wageChange_shouldCalculateAccruedWageCorrectly() {
        // Given
        // 12월 1일 ~ 12월 10일: 시급 10,000원
        val contract1 = WageContract(
            worker = worker,
            hourlyWage = BigDecimal("10000"),
            startDate = LocalDate.of(2025, 12, 1),
            endDate = LocalDate.of(2025, 12, 10)
        )
        // 12월 11일 ~ : 시급 12,000원
        val contract2 = WageContract(
            worker = worker,
            hourlyWage = BigDecimal("12000"),
            startDate = LocalDate.of(2025, 12, 11),
            endDate = null
        )
        
        whenever(wageContractRepository.findByWorker(any())).thenReturn(listOf(contract1, contract2))

        // WorkLog 1: 12월 5일 5시간 (10,000 * 5 = 50,000)
        val workLog1 = WorkLog(
            worker = worker,
            clockInAt = OffsetDateTime.of(2025, 12, 5, 9, 0, 0, 0, java.time.ZoneOffset.ofHours(9)),
            clockOutAt = OffsetDateTime.of(2025, 12, 5, 14, 0, 0, 0, java.time.ZoneOffset.ofHours(9)),
            status = WorkLogStatus.COMPLETED
        )
        // WorkLog 2: 12월 15일 5시간 (12,000 * 5 = 60,000)
        val workLog2 = WorkLog(
            worker = worker,
            clockInAt = OffsetDateTime.of(2025, 12, 15, 9, 0, 0, 0, java.time.ZoneOffset.ofHours(9)),
            clockOutAt = OffsetDateTime.of(2025, 12, 15, 14, 0, 0, 0, java.time.ZoneOffset.ofHours(9)),
            status = WorkLogStatus.COMPLETED
        )

        whenever(workLogRepository.findByOwnerAndTimeRange(any(), eq(monthRange.start), eq(monthRange.endExclusive)))
            .thenReturn(listOf(workLog1, workLog2))
            
        // When
        val result = dashboardReadService.getDashboardSummary(ownerId, targetDate)

        // Then
        // Expected: 50,000 + 60,000 = 110,000
        assertEquals(BigDecimal("110000"), result.month.accruedWage)
    }
    
    @Test
    @DisplayName("Action Count는 미출근 + 미퇴근 + 승인대기 합계여야 함")
    fun summary_actionCount_shouldSumUpIssues() {
        // Given
        // 오늘 스케줄 3, 출근 1 (미출근 2)
        whenever(scheduleRepository.countByOwnerAndTimeRangeExcludingCanceledAndRejected(any(), any(), any()))
            .thenReturn(3L)
        
        // 출근한 1명은 퇴근 안함 (미퇴근 1)
        val ongoingWorkLog = WorkLog(
            worker = worker,
            clockInAt = todayRange.start.plusHours(9),
            clockOutAt = null,
            status = WorkLogStatus.ONGOING
        )
        whenever(workLogRepository.findByOwnerAndTimeRange(any(), eq(todayRange.start), eq(todayRange.endExclusive)))
            .thenReturn(listOf(ongoingWorkLog))
            
        // 승인 대기 스케줄 5개
        whenever(scheduleRepository.findByOwnerAndStatusIn(any(), any()))
            .thenReturn(List(5) { Schedule(worker = worker, startAtUtc = OffsetDateTime.now(), endAtUtc = OffsetDateTime.now().plusHours(1), timezone = "Asia/Seoul") })
            
        // When
        val result = dashboardReadService.getDashboardSummary(ownerId, targetDate)

        // Then
        // 미출근 2 + 미퇴근 1 + 승인대기 5 = 8
        assertEquals(8, result.actionNeededCount)
    }

    @Test
    @DisplayName("Action Center: 승인 대기 스케줄 조회 확인")
    fun actions_pendingApproval_shouldReturnAction() {
        // Given
        val schedule = Schedule(
            id = UUID.randomUUID(),
            worker = worker,
            startAtUtc = OffsetDateTime.now().plusDays(1),
            endAtUtc = OffsetDateTime.now().plusDays(1).plusHours(4),
            timezone = "Asia/Seoul",
            status = ScheduleStatus.REQUESTED_BY_WORKER
        )

        // DashboardReadService uses findByOwnerAndStatusIn
        whenever(scheduleRepository.findByOwnerAndStatusIn(eq(ownerId), any()))
            .thenReturn(listOf(schedule))
            
        // No other issues
        whenever(workLogRepository.findOngoingWorkLogsByOwner(ownerId)).thenReturn(emptyList())
        whenever(scheduleRepository.findByOwnerAndTimeRange(eq(ownerId), any(), any())).thenReturn(emptyList())

        // When
        val result = dashboardReadService.getDashboardActions(ownerId, null, 20, null)

        // Then
        assertEquals(1, result.items.size)
        val item = result.items[0]
        assertEquals(DashboardActionType.PENDING_SCHEDULE_APPROVAL, item.type)
        assertEquals(10, item.priority)
        assertEquals("스케줄 승인 대기", item.title)
    }

    @Test
    @DisplayName("Action Center: 퇴근 미체크 조회 확인")
    fun actions_missingCheckout_shouldReturnUrgentAction() {
        // Given
        // Ongoing worklog, schedule ended 1 hour ago
        val past = OffsetDateTime.now().minusHours(5)
        val schedule = Schedule(
            worker = worker,
            startAtUtc = past,
            endAtUtc = past.plusHours(4), // Ended 1 hour ago
            timezone = "Asia/Seoul"
        )
        val workLog = WorkLog(
            id = UUID.randomUUID(),
            worker = worker,
            schedule = schedule,
            clockInAt = past,
            status = WorkLogStatus.ONGOING
        )

        whenever(scheduleRepository.findByOwnerAndStatusIn(eq(ownerId), any())).thenReturn(emptyList())
        whenever(workLogRepository.findOngoingWorkLogsByOwner(ownerId)).thenReturn(listOf(workLog))
        whenever(scheduleRepository.findByOwnerAndTimeRange(eq(ownerId), any(), any())).thenReturn(emptyList())

        // When
        val result = dashboardReadService.getDashboardActions(ownerId, null, 20, null)

        // Then
        assertEquals(1, result.items.size)
        val item = result.items[0]
        assertEquals(DashboardActionType.MISSING_CHECKOUT, item.type)
        assertEquals(30, item.priority) // Urgent
        assertEquals("퇴근 미체크", item.title)
    }

    @Test
    @DisplayName("Action Center: 우선순위 정렬 확인")
    fun actions_shouldSortByPriority() {
        // Given: 1 pending approval (10), 1 missing checkout (30)
        
        // 1. Pending
        val pendingSchedule = Schedule(
            id = UUID.randomUUID(),
            worker = worker,
            startAtUtc = OffsetDateTime.now().plusDays(1),
            endAtUtc = OffsetDateTime.now().plusDays(1).plusHours(4),
            timezone = "Asia/Seoul",
            status = ScheduleStatus.REQUESTED_BY_WORKER
        )
        whenever(scheduleRepository.findByOwnerAndStatusIn(eq(ownerId), any()))
            .thenReturn(listOf(pendingSchedule))
            
        // 2. Missing Checkout
        val past = OffsetDateTime.now().minusHours(5)
        val pastSchedule = Schedule(
            worker = worker,
            startAtUtc = past,
            endAtUtc = past.plusHours(4),
            timezone = "Asia/Seoul"
        )
        val ongoingLog = WorkLog(
            worker = worker,
            schedule = pastSchedule,
            clockInAt = past,
            status = WorkLogStatus.ONGOING
        )
        whenever(workLogRepository.findOngoingWorkLogsByOwner(ownerId)).thenReturn(listOf(ongoingLog))
        
        // No missing checkins
        whenever(scheduleRepository.findByOwnerAndTimeRange(eq(ownerId), any(), any())).thenReturn(emptyList())

        // When
        val result = dashboardReadService.getDashboardActions(ownerId, null, 20, null)

        // Then
        assertEquals(2, result.items.size)
        // Expected: Missing Checkout (30) -> Pending Approval (10)
        assertEquals(DashboardActionType.MISSING_CHECKOUT, result.items[0].type)
        assertEquals(30, result.items[0].priority)
        
        assertEquals(DashboardActionType.PENDING_SCHEDULE_APPROVAL, result.items[1].type)
        assertEquals(10, result.items[1].priority)
    }

    // --- Attendance API Tests ---

    @Test
    @DisplayName("Attendance: 스케줄 0개 -> 모두 0, 예외 없음")
    fun attendance_zeroState_shouldReturnZeros() {
        // Given
        whenever(scheduleRepository.findByOwnerAndStatusIn(any(), any())).thenReturn(emptyList())

        // When
        val result = dashboardReadService.getAttendanceSummary(ownerId, targetDate, false)

        // Then
        assertEquals(0, result.summary.scheduledCount)
        assertEquals(0, result.summary.checkedInCount)
        assertEquals(0, result.summary.notCheckedInCount)
        assertEquals(0, result.summary.notCheckedOutCount)
        assertEquals(0, result.exceptions.size)
    }

    @Test
    @DisplayName("Attendance: 스케줄 2개, 워크로그 0개 -> 미출근 2, 예외 2개")
    fun attendance_missingCheckin_shouldReturnExceptions() {
        // Given
        val s1 = createSchedule(9, 18)
        val s2 = createSchedule(10, 19)
        whenever(scheduleRepository.findByOwnerAndStatusIn(any(), any())).thenReturn(listOf(s1, s2))
        whenever(workLogRepository.findByScheduleIds(any())).thenReturn(emptyList())

        // When
        val result = dashboardReadService.getAttendanceSummary(ownerId, targetDate, false)

        // Then
        assertEquals(2, result.summary.scheduledCount)
        assertEquals(0, result.summary.checkedInCount)
        assertEquals(2, result.summary.notCheckedInCount)
        assertEquals(2, result.exceptions.size)
        assertEquals(com.worktime.adapter.input.web.dto.dashboard.AttendanceExceptionType.SCHEDULE_EXISTS_BUT_NO_CHECKIN, result.exceptions[0].type)
    }

    @Test
    @DisplayName("Attendance: 스케줄 1개, 출근만 함 -> 미퇴근 1, 예외 1개")
    fun attendance_missingCheckout_shouldReturnException() {
        // Given
        val s1 = createSchedule(9, 18)
        val ongoingLog = WorkLog(worker = worker, schedule = s1, clockInAt = s1.startAtUtc, status = WorkLogStatus.ONGOING)
        
        whenever(scheduleRepository.findByOwnerAndStatusIn(any(), any())).thenReturn(listOf(s1))
        whenever(workLogRepository.findByScheduleIds(any())).thenReturn(listOf(ongoingLog))

        // When
        val result = dashboardReadService.getAttendanceSummary(ownerId, targetDate, false)

        // Then
        assertEquals(1, result.summary.scheduledCount)
        assertEquals(1, result.summary.checkedInCount) // 출근은 했으므로
        assertEquals(0, result.summary.notCheckedInCount)
        assertEquals(1, result.summary.notCheckedOutCount)
        assertEquals(1, result.exceptions.size)
        assertEquals(com.worktime.adapter.input.web.dto.dashboard.AttendanceExceptionType.CHECKIN_EXISTS_BUT_NO_CHECKOUT, result.exceptions[0].type)
    }

    @Test
    @DisplayName("Attendance: 스케줄 1개, 정상 종료 -> 예외 없음")
    fun attendance_completed_shouldReturnNoException() {
        // Given
        val s1 = createSchedule(9, 18)
        val completedLog = WorkLog(worker = worker, schedule = s1, clockInAt = s1.startAtUtc, clockOutAt = s1.endAtUtc, status = WorkLogStatus.COMPLETED)
        
        whenever(scheduleRepository.findByOwnerAndStatusIn(any(), any())).thenReturn(listOf(s1))
        whenever(workLogRepository.findByScheduleIds(any())).thenReturn(listOf(completedLog))

        // When
        val result = dashboardReadService.getAttendanceSummary(ownerId, targetDate, false)

        // Then
        assertEquals(1, result.summary.scheduledCount)
        assertEquals(1, result.summary.checkedInCount)
        assertEquals(0, result.summary.notCheckedInCount)
        assertEquals(0, result.summary.notCheckedOutCount)
        assertEquals(0, result.exceptions.size)
    }

    @Test
    @DisplayName("Attendance: 혼합 (정상 + 미출근 + 미퇴근) -> 정확히 분리")
    fun attendance_mixed_shouldCalculateCorrectly() {
        // Given
        val s1 = createSchedule(9, 18)  // Normal
        val s2 = createSchedule(10, 19) // No Checkin
        val s3 = createSchedule(11, 20) // No Checkout
        
        val log1 = WorkLog(worker = worker, schedule = s1, clockInAt = s1.startAtUtc, clockOutAt = s1.endAtUtc, status = WorkLogStatus.COMPLETED)
        val log3 = WorkLog(worker = worker, schedule = s3, clockInAt = s3.startAtUtc, status = WorkLogStatus.ONGOING)

        whenever(scheduleRepository.findByOwnerAndStatusIn(any(), any())).thenReturn(listOf(s1, s2, s3))
        whenever(workLogRepository.findByScheduleIds(any())).thenReturn(listOf(log1, log3))

        // When
        val result = dashboardReadService.getAttendanceSummary(ownerId, targetDate, false)

        // Then
        assertEquals(3, result.summary.scheduledCount)
        assertEquals(2, result.summary.checkedInCount) // s1, s3
        assertEquals(1, result.summary.notCheckedInCount) // s2
        assertEquals(1, result.summary.notCheckedOutCount) // s3
        
        assertEquals(2, result.exceptions.size)
        // 정렬: StartTime 오름차순. s2(10시, NoCheckIn), s3(11시, NoCheckOut)
        // 하지만 미출근이 먼저 나오는지 정렬 로직 확인 필요 (현재 코드엔 예외 타입별 정렬 없음, 스케줄 순서대로 추가됨)
        // 스케줄은 StartTime 정렬됨 -> s2가 s3보다 먼저? 
        // s1(9시) -> Normal
        // s2(10시) -> Exception(NoCheckin) added
        // s3(11시) -> Exception(NoCheckout) added
        // So Exception List: [NoCheckin, NoCheckout]
        assertEquals(com.worktime.adapter.input.web.dto.dashboard.AttendanceExceptionType.SCHEDULE_EXISTS_BUT_NO_CHECKIN, result.exceptions[0].type)
        assertEquals(com.worktime.adapter.input.web.dto.dashboard.AttendanceExceptionType.CHECKIN_EXISTS_BUT_NO_CHECKOUT, result.exceptions[1].type)
    }

    @Test
    @DisplayName("Attendance: 날짜 범위 밖의 스케줄은 제외됨")
    fun attendance_dateRange_shouldExcludeOutliers() {
        // Given
        // targetDate: 2025-12-15 (Monday)
        val s1 = createSchedule(9, 18) // Today
        val s2 = Schedule(
            worker = worker,
            startAtUtc = OffsetDateTime.of(2025, 12, 16, 9, 0, 0, 0, java.time.ZoneOffset.UTC), // Tomorrow
            endAtUtc = OffsetDateTime.of(2025, 12, 16, 18, 0, 0, 0, java.time.ZoneOffset.UTC),
            timezone = "Asia/Seoul",
            status = ScheduleStatus.CONFIRMED
        )
        
        whenever(scheduleRepository.findByOwnerAndStatusIn(any(), any())).thenReturn(listOf(s1, s2))
        // s1만 타겟이므로 s1 ID로 조회할 것
        whenever(workLogRepository.findByScheduleIds(any())).thenReturn(emptyList())

        // When
        val result = dashboardReadService.getAttendanceSummary(ownerId, targetDate, false)

        // Then
        assertEquals(1, result.summary.scheduledCount) // Only s1
    }

    // Helper
    private fun createSchedule(startHour: Int, endHour: Int): Schedule {
        return Schedule(
            id = UUID.randomUUID(),
            worker = worker,
            // Simple UTC setup for test
            startAtUtc = OffsetDateTime.of(targetDate.year, targetDate.monthValue, targetDate.dayOfMonth, startHour, 0, 0, 0, java.time.ZoneOffset.UTC),
            endAtUtc = OffsetDateTime.of(targetDate.year, targetDate.monthValue, targetDate.dayOfMonth, endHour, 0, 0, 0, java.time.ZoneOffset.UTC),
            timezone = "Asia/Seoul",
            status = ScheduleStatus.CONFIRMED
        )
    }
}


