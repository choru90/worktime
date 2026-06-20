package com.worktime.application.service

import com.worktime.adapter.input.web.dto.worker.WorkerCreateRequest
import com.worktime.adapter.input.web.dto.worker.WorkerUpdateRequest
import com.worktime.adapter.output.persistence.StoreJpaRepository
import com.worktime.application.port.input.WageContractUseCase
import com.worktime.application.port.output.OwnerRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.model.Owner
import com.worktime.domain.model.User
import com.worktime.domain.model.UserRole
import com.worktime.domain.model.Worker
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.*

/**
 * Unit tests for WorkerService.
 */
class WorkerServiceTest {

    private lateinit var workerRepository: WorkerRepository
    private lateinit var wageContractUseCase: WageContractUseCase
    private lateinit var ownerRepository: OwnerRepository
    private lateinit var storeRepository: StoreJpaRepository
    private lateinit var service: WorkerService
    
    private lateinit var testWorker: Worker
    private lateinit var testOwner: Owner
    private val workerId = UUID.randomUUID()
    private val ownerId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val user = User(
        id = userId,
        email = "test@example.com",
        password = "password",
        name = "Owner",
        role = UserRole.OWNER
    )

    @BeforeEach
    fun setUp() {
        workerRepository = mock(WorkerRepository::class.java)
        wageContractUseCase = mock(WageContractUseCase::class.java)
        ownerRepository = mock(OwnerRepository::class.java)
        storeRepository = mock(StoreJpaRepository::class.java)
        service = WorkerService(workerRepository, wageContractUseCase, ownerRepository, storeRepository)
        
        testOwner = Owner(
            id = ownerId,
            user = user
        )
        
        testWorker = Worker(
            id = workerId,
            name = "Test Worker",
            phoneNumber = "01012345678",
            owner = testOwner
        )
    }

    @Test
    fun `createWorker - should create worker successfully`() {
        // Given
        val request = WorkerCreateRequest(
            name = "Hong Gil Dong",
            phoneNumber = "01098765432"
        )
        
        whenever(ownerRepository.findByUserId(ownerId)).thenReturn(testOwner)
        whenever(workerRepository.save(any())).thenAnswer { it.arguments[0] }
        
        // When
        val result = service.createWorker(ownerId, request)
        
        // Then
        assertNotNull(result)
        assertEquals("Hong Gil Dong", result.name)
        assertEquals("01098765432", result.phoneNumber)
        assertTrue(result.active)
        verify(workerRepository, times(1)).save(any())
    }

    @Test
    fun `updateWorker - should update worker successfully`() {
        // Given
        val request = WorkerUpdateRequest(
            name = "Updated Name",
            phoneNumber = "01011112222"
        )
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(workerRepository.save(any())).thenAnswer { it.arguments[0] }
        
        // When
        val result = service.updateWorker(workerId, request)
        
        // Then
        assertEquals("Updated Name", result.name)
        assertEquals("01011112222", result.phoneNumber)
        verify(workerRepository, times(1)).save(any())
    }

   @Test
    fun `updateWorker - should throw ResourceNotFoundException when worker not found`() {
        // Given
        val request = WorkerUpdateRequest(
            name = "Updated Name",
            phoneNumber = "01011112222"
        )
        
        whenever(workerRepository.findById(workerId)).thenReturn(null)
        
        // When & Then
        assertThrows<ResourceNotFoundException> {
            service.updateWorker(workerId, request)
        }
    }

    @Test
    fun `deleteWorker - should soft delete worker`() {
        // Given
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(workerRepository.save(any())).thenAnswer { it.arguments[0] }
        
        // When
        service.deleteWorker(workerId)
        
        // Then
        assertFalse(testWorker.active)
        verify(workerRepository, times(1)).save(testWorker)
    }

    @Test
    fun `getWorkerById - should return worker`() {
        // Given
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        
        // When
        val result = service.getWorkerById(workerId)
        
        // Then
        assertEquals(workerId, result.id)
        assertEquals("Test Worker", result.name)
    }

    @Test
    fun `getAllWorkers - should return all active workers when filter is true`() {
        // Given
        val activeWorkers = listOf(testWorker)
        whenever(ownerRepository.findByUserId(userId)).thenReturn(testOwner)
        whenever(workerRepository.findByOwner(ownerId)).thenReturn(activeWorkers)
        
        // When
        val result = service.getAllWorkers(userId = userId, active = true)
        
        // Then
        assertEquals(1, result.size)
        assertEquals(workerId, result[0].id)
    }
}
