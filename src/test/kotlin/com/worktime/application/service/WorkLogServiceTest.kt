package com.worktime.application.service

import com.worktime.adapter.input.web.dto.worklog.ClockInRequest
import com.worktime.adapter.input.web.dto.worklog.ClockOutRequest
import com.worktime.application.port.output.ScheduleRepository
import com.worktime.application.port.output.WorkLogRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.exception.InvalidWorkLogStateException
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.exception.WorkLogConflictException
import com.worktime.domain.model.WorkLog
import com.worktime.domain.model.WorkLogStatus
import com.worktime.domain.model.Worker
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * Unit tests for WorkLogService
 */
class WorkLogServiceTest {
    
    private lateinit var workLogRepository: WorkLogRepository
    private lateinit var workerRepository: WorkerRepository
    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var service: WorkLogService
    
    private lateinit var testWorker: Worker
    private val workerId = UUID.randomUUID()
    
    @BeforeEach
    fun setUp() {
        workLogRepository = mock(WorkLogRepository::class.java)
        workerRepository = mock(WorkerRepository::class.java)
        scheduleRepository = mock(ScheduleRepository::class.java)
        service = WorkLogService(workLogRepository, workerRepository, scheduleRepository)
        
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
    fun `clockIn - should clock in successfully when no ongoing work log`() {
        // Given
        val request = ClockInRequest(
            scheduleId = null,
            clockInAt = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC)
        )
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(workLogRepository.hasOngoingWorkLog(testWorker)).thenReturn(false)
        whenever(workLogRepository.save(any())).thenAnswer { it.arguments[0] }
        
        // When
        val result = service.clockIn(workerId, request)
        
        // Then
        assertNotNull(result)
        assertEquals(workerId, result.workerId)
        verify(workLogRepository, times(1)).save(any())
    }
    
    @Test
    fun `clockIn - should throw WorkLogConflictException when ongoing work log exists`() {
        // Given
        val request = ClockInRequest(
            scheduleId = null,
            clockInAt = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC)
        )
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(workLogRepository.hasOngoingWorkLog(testWorker)).thenReturn(true)
        
        // When & Then
        val exception = assertThrows<WorkLogConflictException> {
            service.clockIn(workerId, request)
        }
        
        assertTrue(exception.message!!.contains("ongoing"))
        verify(workLogRepository, never()).save(any())
    }
    
    @Test
    fun `clockIn - should throw ResourceNotFoundException when worker not found`() {
        // Given
        val request = ClockInRequest(
            scheduleId = null,
            clockInAt = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC)
        )
        
        whenever(workerRepository.findById(workerId)).thenReturn(null)
        
        // When & Then
        assertThrows<ResourceNotFoundException> {
            service.clockIn(workerId, request)
        }
    }
    
    @Test
    fun `clockOut - should clock out successfully when ongoing work log exists`() {
        // Given
        val clockInTime = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC)
        val clockOutTime = OffsetDateTime.of(2025, 1, 15, 17, 0, 0, 0, ZoneOffset.UTC)
        
        val workLog = WorkLog(
            worker = testWorker,
            clockInAt = clockInTime,
            status = WorkLogStatus.ONGOING
        )
        
        val request = ClockOutRequest(clockOutAt = clockOutTime)
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(workLogRepository.findCurrentOngoingWorkLog(testWorker)).thenReturn(workLog)
        whenever(workLogRepository.save(any())).thenAnswer { it.arguments[0] }
        
        // When
        val result = service.clockOut(workerId, request)
        
        // Then
        assertNotNull(result)
        assertNotNull(result.clockOutAt)
        verify(workLogRepository, times(1)).save(any())
    }
    
    @Test
    fun `clockOut - should throw InvalidWorkLogStateException when no ongoing work log`() {
        // Given
        val request = ClockOutRequest(
            clockOutAt = OffsetDateTime.of(2025, 1, 15, 17, 0, 0, 0, ZoneOffset.UTC)
        )
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(workLogRepository.findCurrentOngoingWorkLog(testWorker)).thenReturn(null)
        
        // When & Then
        val exception = assertThrows<InvalidWorkLogStateException> {
            service.clockOut(workerId, request)
        }
        
        assertTrue(exception.message!!.contains("No ongoing"))
    }
    
    @Test
    fun `clockOut - should throw InvalidWorkLogStateException when clockOut is before clockIn`() {
        // Given
        val clockInTime = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC)
        val clockOutTime = OffsetDateTime.of(2025, 1, 15, 8, 0, 0, 0, ZoneOffset.UTC) // Earlier!
        
        val workLog = WorkLog(
            worker = testWorker,
            clockInAt = clockInTime,
            status = WorkLogStatus.ONGOING
        )
        
        val request = ClockOutRequest(clockOutAt = clockOutTime)
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(workLogRepository.findCurrentOngoingWorkLog(testWorker)).thenReturn(workLog)
        
        // When & Then
        val exception = assertThrows<InvalidWorkLogStateException> {
            service.clockOut(workerId, request)
        }
        
        assertTrue(exception.message!!.contains("after"))
    }
    
    @Test
    fun `getAllWorkLogs - should include synthetic NO_SHOW when schedule confirmed but no work log`() {
        // Given
        val fromTime = OffsetDateTime.of(2025, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC)
        val toTime = OffsetDateTime.of(2025, 1, 15, 23, 59, 59, 0, ZoneOffset.UTC)
        
        val schedule = com.worktime.domain.model.Schedule(
            worker = testWorker,
            startAtUtc = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC),
            endAtUtc = OffsetDateTime.of(2025, 1, 15, 18, 0, 0, 0, ZoneOffset.UTC),
            timezone = "Asia/Seoul",
            status = com.worktime.domain.model.ScheduleStatus.CONFIRMED
        )
        
        whenever(workLogRepository.findByTimeRange(fromTime, toTime)).thenReturn(emptyList())
        whenever(scheduleRepository.findByTimeRange(fromTime, toTime)).thenReturn(listOf(schedule))
        
        // When
        val resultList = service.getAllWorkLogs(fromTime, toTime, null, null)
        
        // Then
        assertEquals(1, resultList.size)
        val result = resultList.first()
        assertEquals(WorkLogStatus.NO_SHOW, result.status)
        assertEquals(testWorker.id, result.workerId)
        assertEquals(schedule.id, result.scheduleId)
        assertEquals(schedule.startAtUtc, result.clockInAt)
    }

    @Test
    fun `getAllWorkLogs - should include synthetic NO_SHOW when schedule is pending approval`() {
        // Given
        val fromTime = OffsetDateTime.of(2025, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC)
        val toTime = OffsetDateTime.of(2025, 1, 15, 23, 59, 59, 0, ZoneOffset.UTC)
        
        val schedule = com.worktime.domain.model.Schedule(
            worker = testWorker,
            startAtUtc = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC),
            endAtUtc = OffsetDateTime.of(2025, 1, 15, 18, 0, 0, 0, ZoneOffset.UTC),
            timezone = "Asia/Seoul",
            status = com.worktime.domain.model.ScheduleStatus.REQUESTED_BY_WORKER
        )
        
        whenever(workLogRepository.findByTimeRange(fromTime, toTime)).thenReturn(emptyList())
        whenever(scheduleRepository.findByTimeRange(fromTime, toTime)).thenReturn(listOf(schedule))
        
        // When
        val resultList = service.getAllWorkLogs(fromTime, toTime, null, null)
        
        // Then
        assertEquals(1, resultList.size)
        val result = resultList.first()
        assertEquals(WorkLogStatus.NO_SHOW, result.status)
    }

    @Test
    fun `getAllWorkLogs - should return only actual work logs when schedule is matched`() {
        // Given
        val fromTime = OffsetDateTime.of(2025, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC)
        val toTime = OffsetDateTime.of(2025, 1, 15, 23, 59, 59, 0, ZoneOffset.UTC)
        
        val schedule = com.worktime.domain.model.Schedule(
            worker = testWorker,
            startAtUtc = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC),
            endAtUtc = OffsetDateTime.of(2025, 1, 15, 18, 0, 0, 0, ZoneOffset.UTC),
            timezone = "Asia/Seoul",
            status = com.worktime.domain.model.ScheduleStatus.CONFIRMED
        )
        
        val workLog = WorkLog(
            worker = testWorker,
            schedule = schedule,
            clockInAt = OffsetDateTime.of(2025, 1, 15, 8, 55, 0, 0, ZoneOffset.UTC),
            clockOutAt = OffsetDateTime.of(2025, 1, 15, 18, 5, 0, 0, ZoneOffset.UTC),
            status = WorkLogStatus.COMPLETED
        )
        
        whenever(workLogRepository.findByTimeRange(fromTime, toTime)).thenReturn(listOf(workLog))
        whenever(scheduleRepository.findByTimeRange(fromTime, toTime)).thenReturn(listOf(schedule))
        
        // When
        val resultList = service.getAllWorkLogs(fromTime, toTime, null, null)
        
        // Then
        assertEquals(1, resultList.size)
        val result = resultList.first()
        assertEquals(WorkLogStatus.COMPLETED, result.status)
        assertEquals(workLog.id, result.id)
    }
}
