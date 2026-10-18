package com.worktime.application.service

import com.worktime.adapter.input.web.dto.wage.WageContractCreateRequest
import com.worktime.application.port.output.WageContractRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.exception.BusinessException
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.model.WageContract
import com.worktime.domain.model.Worker
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

/**
 * Unit tests for WageContractService.
 */
class WageContractServiceTest {

    private lateinit var wageContractRepository: WageContractRepository
    private lateinit var workerRepository: WorkerRepository
    private lateinit var service: WageContractService
    
    private lateinit var testWorker: Worker
    private val workerId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        wageContractRepository = mock(WageContractRepository::class.java)
        workerRepository = mock(WorkerRepository::class.java)
        service = WageContractService(wageContractRepository, workerRepository)
        
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
    fun `createWageContract - should create contract successfully when no overlap`() {
        // Given
        val request = WageContractCreateRequest(
            hourlyWage = BigDecimal("10000"),
            startDate = LocalDate.of(2025, 1, 1),
            endDate = LocalDate.of(2025, 12, 31)
        )
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(wageContractRepository.findContractsOverlappingPeriod(any(), any(), any()))
            .thenReturn(emptyList())
        whenever(wageContractRepository.save(any())).thenAnswer { it.arguments[0] }
        
        // When
        val result = service.createWageContract(workerId, request)
        
        // Then
        assertNotNull(result)
        assertEquals(BigDecimal("10000"), result.hourlyWage)
        assertEquals(LocalDate.of(2025, 1, 1), result.startDate)
        verify(wageContractRepository, times(1)).save(any())
    }

    @Test
    fun `createWageContract - should throw BusinessException when period overlaps`() {
        // Given
        val request = WageContractCreateRequest(
            hourlyWage = BigDecimal("10000"),
            startDate = LocalDate.of(2025, 1, 1),
            endDate = LocalDate.of(2025, 12, 31)
        )
        
        val existingContract = WageContract(
            worker = testWorker,
            hourlyWage = BigDecimal("9000"),
            startDate = LocalDate.of(2025, 6, 1),
            endDate = LocalDate.of(2025, 12, 31)
        )
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(wageContractRepository.findContractsOverlappingPeriod(any(), any(), any()))
            .thenReturn(listOf(existingContract))
        
        // When & Then
        val exception = assertThrows<BusinessException> {
            service.createWageContract(workerId, request)
        }
        
        assertTrue(exception.message!!.contains("overlap"))
        verify(wageContractRepository, never()).save(any())
    }

    @Test
    fun `createWageContract - should throw ResourceNotFoundException when worker not found`() {
        // Given
        val request = WageContractCreateRequest(
            hourlyWage = BigDecimal("10000"),
            startDate = LocalDate.of(2025, 1, 1),
            endDate = null
        )
        
        whenever(workerRepository.findById(workerId)).thenReturn(null)
        
        // When & Then
        assertThrows<ResourceNotFoundException> {
            service.createWageContract(workerId, request)
        }
    }

    @Test
    fun `new wage closes previous open contract without rejecting its overlap`() {
        val previous = WageContract(worker = testWorker, hourlyWage = BigDecimal("9000"),
            startDate = LocalDate.of(2025, 1, 1))
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(wageContractRepository.findCurrentActiveContract(testWorker)).thenReturn(previous)
        whenever(wageContractRepository.findContractsOverlappingPeriod(any(), any(), any()))
            .thenReturn(listOf(previous))
        whenever(wageContractRepository.save(any())).thenAnswer { it.arguments[0] }

        service.createWageContract(workerId, WageContractCreateRequest(
            BigDecimal("10000"), LocalDate.of(2025, 6, 1), null))

        assertEquals(LocalDate.of(2025, 5, 31), previous.endDate)
        verify(wageContractRepository, times(2)).save(any())
    }

    @Test
    fun `conflicting historical contract leaves open contract unchanged`() {
        val previous = WageContract(worker = testWorker, hourlyWage = BigDecimal("9000"),
            startDate = LocalDate.of(2025, 1, 1))
        val historical = WageContract(worker = testWorker, hourlyWage = BigDecimal("8000"),
            startDate = LocalDate.of(2024, 1, 1), endDate = LocalDate.of(2024, 12, 31))
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(wageContractRepository.findCurrentActiveContract(testWorker)).thenReturn(previous)
        whenever(wageContractRepository.findContractsOverlappingPeriod(any(), any(), any()))
            .thenReturn(listOf(previous, historical))

        assertThrows<BusinessException> {
            service.createWageContract(workerId, WageContractCreateRequest(
                BigDecimal("10000"), LocalDate.of(2024, 6, 1), null))
        }
        assertNull(previous.endDate)
        verify(wageContractRepository, never()).save(any())
    }

    @Test
    fun `invalid contract period cannot modify existing contract`() {
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        assertThrows<BusinessException> {
            service.createWageContract(workerId, WageContractCreateRequest(
                BigDecimal("10000"), LocalDate.of(2025, 6, 1), LocalDate.of(2025, 5, 31)))
        }
        verifyNoInteractions(wageContractRepository)
    }

    @Test
    fun `getWageOnDate - should return wage info when contract exists`() {
        // Given
        val queryDate = LocalDate.of(2025, 6, 15)
        val contract = WageContract(
            worker = testWorker,
            hourlyWage = BigDecimal("10000"),
            startDate = LocalDate.of(2025, 1, 1),
            endDate = null
        )
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(wageContractRepository.findActiveContractOnDate(testWorker, queryDate))
            .thenReturn(contract)
        
        // When
        val result = service.getWageOnDate(workerId, queryDate)
        
        // Then
        assertEquals(queryDate, result.date)
        assertEquals(BigDecimal("10000"), result.hourlyWage)
        assertTrue(result.hasContract)
    }

    @Test
    fun `getWageOnDate - should return null wage when no contract exists`() {
        // Given
        val queryDate = LocalDate.of(2025, 6, 15)
        
        whenever(workerRepository.findById(workerId)).thenReturn(testWorker)
        whenever(wageContractRepository.findActiveContractOnDate(testWorker, queryDate))
            .thenReturn(null)
        
        // When
        val result = service.getWageOnDate(workerId, queryDate)
        
        // Then
        assertEquals(queryDate, result.date)
        assertNull(result.hourlyWage)
        assertFalse(result.hasContract)
    }
}
