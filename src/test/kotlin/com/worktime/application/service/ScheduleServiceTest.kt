package com.worktime.application.service

import com.worktime.adapter.input.web.dto.schedule.ScheduleCreateRequest
import com.worktime.adapter.input.web.dto.schedule.ScheduleRequestType
import com.worktime.application.port.output.OwnerRepository
import com.worktime.application.port.output.ScheduleRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.exception.InvalidScheduleStateException
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.exception.ScheduleConflictException
import com.worktime.domain.model.Schedule
import com.worktime.domain.model.ScheduleStatus
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
 * Unit tests for ScheduleService
 */
class ScheduleServiceTest {
    
    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var workerRepository: WorkerRepository
    private lateinit var ownerRepository: OwnerRepository
    private lateinit var notificationService: NotificationService
    private lateinit var service: ScheduleService
    
    private lateinit var testWorker: Worker
    private val workerId = UUID.randomUUID()
    
    @BeforeEach
    fun setUp() {
        scheduleRepository = mock(ScheduleRepository::class.java)
        workerRepository = mock(WorkerRepository::class.java)
        ownerRepository = mock(OwnerRepository::class.java)
        notificationService = mock(NotificationService::class.java)
        service = ScheduleService(scheduleRepository, workerRepository, ownerRepository, notificationService)
        
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
    fun `createSchedule - should create worker request schedule successfully when no conflict`() {
        // Given
        val request = ScheduleCreateRequest(
            workerId = workerId,
            startAtUtc = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC),
            endAtUtc = OffsetDateTime.of(2025, 1, 15, 17, 0, 0, 0, ZoneOffset.UTC),
            timezone = "Asia/Seoul",
            requestType = ScheduleRequestType.WORKER_REQUEST
        )
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(scheduleRepository.findConfirmedSchedulesOverlapping(any(), any(), any()))
            .thenReturn(emptyList())
        whenever(scheduleRepository.save(any())).thenAnswer { it.arguments[0] }
        
        // When
        val result = service.createSchedule(request)
        
        // Then
        assertNotNull(result)
        assertEquals(workerId, result.workerId)
        verify(scheduleRepository, times(1)).save(any())
    }
    
    @Test
    fun `createSchedule - should throw ScheduleConflictException when time overlaps with confirmed schedule`() {
        // Given
        val request = ScheduleCreateRequest(
            workerId = workerId,
            startAtUtc = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC),
            endAtUtc = OffsetDateTime.of(2025, 1, 15, 17, 0, 0, 0, ZoneOffset.UTC),
            timezone = "Asia/Seoul",
            requestType = ScheduleRequestType.WORKER_REQUEST
        )
        
        val existingSchedule = Schedule(
            worker = testWorker,
            startAtUtc = OffsetDateTime.of(2025, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC),
            endAtUtc = OffsetDateTime.of(2025, 1, 15, 20, 0, 0, 0, ZoneOffset.UTC),
            timezone = "Asia/Seoul",
            status = ScheduleStatus.CONFIRMED
        )
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(scheduleRepository.findConfirmedSchedulesOverlapping(any(), any(), any()))
            .thenReturn(listOf(existingSchedule))
        
        // When & Then
        val exception = assertThrows<ScheduleConflictException> {
            service.createSchedule(request)
        }
        
        assertTrue(exception.message!!.contains("conflict"))
        verify(scheduleRepository, never()).save(any())
    }
    
    @Test
    fun `createSchedule - should throw ResourceNotFoundException when worker not found`() {
        // Given
        val request = ScheduleCreateRequest(
            workerId = workerId,
            startAtUtc = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC),
            endAtUtc = OffsetDateTime.of(2025, 1, 15, 17, 0, 0, 0, ZoneOffset.UTC),
            timezone = "Asia/Seoul",
            requestType = ScheduleRequestType.WORKER_REQUEST
        )
        
        whenever(workerRepository.findById(workerId)).thenReturn(null)
        
        // When & Then
        assertThrows<ResourceNotFoundException> {
            service.createSchedule(request)
        }
    }
    
    @Test
    fun `confirmSchedule - should confirm schedule successfully when in valid state`() {
        // Given
        val scheduleId = UUID.randomUUID()
        val schedule = Schedule(
            id = scheduleId,
            worker = testWorker,
            startAtUtc = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC),
            endAtUtc = OffsetDateTime.of(2025, 1, 15, 17, 0, 0, 0, ZoneOffset.UTC),
            timezone = "Asia/Seoul",
            status = ScheduleStatus.REQUESTED_BY_WORKER
        )
        
        whenever(scheduleRepository.findById(scheduleId)).thenReturn(schedule)
        whenever(scheduleRepository.save(any())).thenAnswer { it.arguments[0] }
        
        // When
        val result = service.confirmSchedule(scheduleId)
        
        // Then
        assertNotNull(result)
        verify(scheduleRepository, times(1)).save(any())
    }
    
    @Test
    fun `confirmSchedule - should throw InvalidScheduleStateException when already confirmed`() {
        // Given
        val scheduleId = UUID.randomUUID()
        val schedule = Schedule(
            id = scheduleId,
            worker = testWorker,
            startAtUtc = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC),
            endAtUtc = OffsetDateTime.of(2025, 1, 15, 17, 0, 0, 0, ZoneOffset.UTC),
            timezone = "Asia/Seoul",
            status = ScheduleStatus.CONFIRMED
        )
        
        whenever(scheduleRepository.findById(scheduleId)).thenReturn(schedule)
        
        // When & Then
        assertThrows<InvalidScheduleStateException> {
            service.confirmSchedule(scheduleId)
        }
    }
    
    @Test
    fun `cancelSchedule - should cancel confirmed schedule successfully`() {
        // Given
        val scheduleId = UUID.randomUUID()
        val schedule = Schedule(
            id = scheduleId,
            worker = testWorker,
            startAtUtc = OffsetDateTime.of(2025, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC),
            endAtUtc = OffsetDateTime.of(2025, 1, 15, 17, 0, 0, 0, ZoneOffset.UTC),
            timezone = "Asia/Seoul",
            status = ScheduleStatus.CONFIRMED
        )
        
        whenever(scheduleRepository.findById(scheduleId)).thenReturn(schedule)
        whenever(scheduleRepository.save(any())).thenAnswer { it.arguments[0] }
        
        // When
        val result = service.cancelSchedule(scheduleId)
        
        // Then
        assertNotNull(result)
        verify(scheduleRepository, times(1)).save(any())
    }
}
