package com.worktime.application.service

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
}
