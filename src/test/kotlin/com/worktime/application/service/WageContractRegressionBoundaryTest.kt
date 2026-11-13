package com.worktime.application.service

import com.worktime.application.port.output.*
import com.worktime.adapter.input.web.dto.wage.WageContractCreateRequest
import com.worktime.domain.model.*
import com.worktime.domain.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class WageContractRegressionBoundaryTest {
    private val contracts=mock<WageContractRepository>();private val workers=mock<WorkerRepository>()
    private val service=WageContractService(contracts,workers)
    private val worker=Worker(name="W",phoneNumber="000",owner=Owner(user=User(email="o@test",password="pw",name="O",role=UserRole.OWNER)))
    private val day=LocalDate.of(2024,1,1)
    @Test fun `new active contract cannot start at or before existing active contract start`() {
        whenever(workers.findById(worker.id)).thenReturn(worker)
        val current=WageContract(worker=worker,hourlyWage=BigDecimal.TEN,startDate=day)
        whenever(contracts.findCurrentActiveContract(worker)).thenReturn(current)
        for(date in listOf(day,day.minusDays(1))) {
            assertThrows<BusinessException> { service.createWageContract(worker.id,WageContractCreateRequest(BigDecimal("10000"),date)) }
            assertNull(current.endDate)
        }
        verify(contracts,never()).save(any())
    }
    @Test fun `wage on date exposes absent contract explicitly and missing worker is rejected`() {
        whenever(workers.findById(worker.id)).thenReturn(worker)
        val result=service.getWageOnDate(worker.id,day)
        assertEquals(day,result.date);assertNull(result.hourlyWage);assertFalse(result.hasContract)
        assertThrows<ResourceNotFoundException> { service.getWageOnDate(UUID.randomUUID(),day) }
    }
}
