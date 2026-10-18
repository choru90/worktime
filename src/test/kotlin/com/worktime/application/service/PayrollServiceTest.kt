package com.worktime.application.service

import com.worktime.adapter.input.web.dto.wage.WageInfoResponse
import com.worktime.application.port.input.WageContractUseCase
import com.worktime.application.port.output.OwnerRepository
import com.worktime.application.port.output.WorkLogRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.exception.BusinessException
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.model.WorkLog
import com.worktime.domain.model.Worker
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * Unit tests for PayrollService
 */
class PayrollServiceTest {
    
    private lateinit var workLogRepository: WorkLogRepository
    private lateinit var workerRepository: WorkerRepository
    private lateinit var ownerRepository: OwnerRepository
    private lateinit var wageContractUseCase: WageContractUseCase
    private lateinit var service: PayrollService
    
    private lateinit var testWorker: Worker
    private val workerId = UUID.randomUUID()
    
    @BeforeEach
    fun setUp() {
        workLogRepository = mock(WorkLogRepository::class.java)
        workerRepository = mock(WorkerRepository::class.java)
        ownerRepository = mock(OwnerRepository::class.java)
        wageContractUseCase = mock(WageContractUseCase::class.java)
        service = PayrollService(workLogRepository, workerRepository, ownerRepository, wageContractUseCase)
        
        testWorker = Worker(
            id = workerId,
            name = "Test Worker",
            phoneNumber = "01012345678",
            owner = com.worktime.domain.model.Owner(
                id = UUID.randomUUID(),
                user = com.worktime.domain.model.User(
                    id = UUID.randomUUID(),
                    email = "owner@test.com",
                    password = "pw",
                    name = "Owner",
                    role = com.worktime.domain.model.UserRole.OWNER
                )
            )
        )
    }
    
    @Test
    fun `getWorkSummary - should return work summary successfully`() {
        // Given
        val from = LocalDate.of(2025, 1, 1)
        val to = LocalDate.of(2025, 1, 31)
        
        val clockInTime1 = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC)
        val clockOutTime1 = OffsetDateTime.of(2025, 1, 15, 17, 0, 0, 0, ZoneOffset.UTC)
        val workLog1 = WorkLog(
            worker = testWorker,
            clockInAt = clockInTime1
        )
        workLog1.clockOut(clockOutTime1)
        
        val clockInTime2 = OffsetDateTime.of(2025, 1, 16, 9, 0, 0, 0, ZoneOffset.UTC)
        val clockOutTime2 = OffsetDateTime.of(2025, 1, 16, 13, 0, 0, 0, ZoneOffset.UTC)
        val workLog2 = WorkLog(
            worker = testWorker,
            clockInAt = clockInTime2
        )
        workLog2.clockOut(clockOutTime2)
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(workLogRepository.findCompletedWorkLogsInRange(any(), any(), any()))
            .thenReturn(listOf(workLog1, workLog2))
        
        // When
        val result = service.getWorkSummary(workerId, from, to)
        
        // Then
        assertNotNull(result)
        assertEquals(workerId, result.workerId)
        assertEquals(2, result.workLogCount)
        assertTrue(result.totalMinutes > 0)
        assertEquals(2, result.dailySummaries.size)
    }
    
    @Test
    fun `getWorkSummary - should throw BusinessException when from is after to`() {
        // Given
        val from = LocalDate.of(2025, 1, 31)
        val to = LocalDate.of(2025, 1, 1)
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        
        // When & Then
        val exception = assertThrows<BusinessException> {
            service.getWorkSummary(workerId, from, to)
        }
        
        assertTrue(exception.message!!.contains("before"))
    }
    
    @Test
    fun `calculatePayroll - should calculate payroll successfully with wage contracts`() {
        // Given
        val from = LocalDate.of(2025, 1, 1)
        val to = LocalDate.of(2025, 1, 31)
        
        val clockInTime = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC)
        val clockOutTime = OffsetDateTime.of(2025, 1, 15, 17, 0, 0, 0, ZoneOffset.UTC)
        val workLog = WorkLog(
            worker = testWorker,
            clockInAt = clockInTime
        )
        workLog.clockOut(clockOutTime)
        
        val wageInfo = WageInfoResponse(
            date = LocalDate.of(2025, 1, 15),
            hourlyWage = BigDecimal("10000"),
            hasContract = true
        )
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(workLogRepository.findConfirmedWorkLogsInRange(any(), any(), any()))
            .thenReturn(listOf(workLog))
        whenever(wageContractUseCase.getWageOnDate(any(), any())).thenReturn(wageInfo)
        
        // When
        val result = service.calculatePayroll(workerId, from, to)
        
        // Then
        assertNotNull(result)
        assertEquals(workerId, result.workerId)
        assertTrue(result.totalPay > BigDecimal.ZERO)
        assertEquals(1, result.wageDetails.size)
        assertEquals(BigDecimal("10000"), result.wageDetails[0].hourlyWage)
    }
    
    @Test
    fun `calculatePayroll - should handle work logs with no wage contract`() {
        // Given
        val from = LocalDate.of(2025, 1, 1)
        val to = LocalDate.of(2025, 1, 31)
        
        val clockInTime = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC)
        val clockOutTime = OffsetDateTime.of(2025, 1, 15, 17, 0, 0, 0, ZoneOffset.UTC)
        val workLog = WorkLog(
            worker = testWorker,
            clockInAt = clockInTime
        )
        workLog.clockOut(clockOutTime)
        
        val wageInfo = WageInfoResponse(
            date = LocalDate.of(2025, 1, 15),
            hourlyWage = null,
            hasContract = false
        )
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(workLogRepository.findConfirmedWorkLogsInRange(any(), any(), any()))
            .thenReturn(listOf(workLog))
        whenever(wageContractUseCase.getWageOnDate(any(), any())).thenReturn(wageInfo)
        
        // When
        val result = service.calculatePayroll(workerId, from, to)
        
        // Then
        assertNotNull(result)
        assertEquals(BigDecimal.ZERO, result.totalPay)
        assertNull(result.wageDetails[0].hourlyWage)
    }
    
    @Test
    fun `calculatePayroll - should throw ResourceNotFoundException when worker not found`() {
        // Given
        val from = LocalDate.of(2025, 1, 1)
        val to = LocalDate.of(2025, 1, 31)
        
        whenever(workerRepository.findById(workerId)).thenReturn(null)
        
        // When & Then
        assertThrows<ResourceNotFoundException> {
            service.calculatePayroll(workerId, from, to)
        }
    }
}
