package com.worktime.application.service

import com.worktime.application.port.output.*
import com.worktime.application.port.input.WageContractUseCase
import com.worktime.adapter.output.persistence.StoreJpaRepository
import com.worktime.adapter.input.web.dto.worker.*
import com.worktime.adapter.input.web.dto.wage.WageContractResponse
import com.worktime.domain.model.*
import com.worktime.domain.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.LocalDate
import java.util.*

class WorkerBoundaryTest {
    private val workers=mock<WorkerRepository>();private val owners=mock<OwnerRepository>()
    private val stores=mock<StoreJpaRepository>();private val wages=mock<WageContractUseCase>()
    private val service=WorkerService(workers,wages,owners,stores)
    private val owner=Owner(user=User(email="o@test",password="pw",name="O",role=UserRole.OWNER))
    private val worker=Worker(name="W",phoneNumber="000",owner=owner)
    private fun setup() {
        whenever(workers.findById(worker.id)).thenReturn(worker)
        whenever(owners.findByUserId(owner.user.id)).thenReturn(owner)
        whenever(workers.save(any())).thenAnswer { it.arguments[0] as Worker }
    }
    @Test fun `missing owner and worker are rejected by every service entrypoint`() {
        val id=UUID.randomUUID()
        assertThrows<ResourceNotFoundException> { service.createWorker(id,WorkerCreateRequest("W","000")) }
        assertThrows<ResourceNotFoundException> { service.getAllWorkers(id,null,null) }
        assertThrows<ResourceNotFoundException> { service.updateWorker(id,WorkerUpdateRequest("W","000")) }
        assertThrows<ResourceNotFoundException> { service.deleteWorker(id) }
        assertThrows<ResourceNotFoundException> { service.resignWorker(id,LocalDate.now()) }
        assertThrows<ResourceNotFoundException> { service.getWorkerById(id) }
        assertThrows<ResourceNotFoundException> { service.getWorkerDetail(id) }
        assertThrows<ResourceNotFoundException> { service.generateInviteCode(id) }
        verifyNoInteractions(wages)
    }
    @Test fun `store binding rejects missing and foreign store on creation and update`() {
        setup();val foreign=Store(owner=Owner(user=owner.user),name="Foreign")
        whenever(stores.findById(foreign.id)).thenReturn(Optional.of(foreign))
        val id=UUID.randomUUID();whenever(stores.findById(id)).thenReturn(Optional.empty())
        for(storeId in listOf(id,foreign.id)) {
            assertThrows<ResourceNotFoundException> { service.createWorker(owner.user.id,WorkerCreateRequest("W","000",storeId)) }
            assertThrows<ResourceNotFoundException> { service.updateWorker(worker.id,WorkerUpdateRequest("W","000",storeId)) }
        }
        verify(workers,never()).save(any())
    }
    @Test fun `worker listing filters foreign store records and each active combination`() {
        setup();val store=Store(owner=owner,name="Cafe")
        val inactive=Worker(name="Inactive",phoneNumber="001",owner=owner,active=false)
        val foreign=Worker(name="Foreign",phoneNumber="002",owner=Owner(user=owner.user))
        whenever(workers.findByStoreId(store.id)).thenReturn(listOf(worker,inactive,foreign))
        whenever(workers.findByOwner(owner.id)).thenReturn(listOf(worker,inactive))
        assertEquals(setOf(worker.id,inactive.id),service.getAllWorkers(owner.user.id,null,store.id).map { it.id }.toSet())
        assertEquals(listOf(worker.id),service.getAllWorkers(owner.user.id,true,store.id).map { it.id })
        assertEquals(listOf(inactive.id),service.getAllWorkers(owner.user.id,false,store.id).map { it.id })
        assertEquals(listOf(worker.id),service.getAllWorkers(owner.user.id,true,null).map { it.id })
        assertEquals(listOf(inactive.id),service.getAllWorkers(owner.user.id,false,null).map { it.id })
    }
    @Test fun `linked worker cannot regenerate invite and missing current contract permits resignation`() {
        setup();worker.userAccount=owner.user
        assertThrows<BusinessException> { service.generateInviteCode(worker.id) };verify(workers,never()).save(any())
        val result=service.resignWorker(worker.id,LocalDate.of(2025,1,1))
        assertFalse(result.active);assertFalse(worker.active);verify(wages,never()).terminateWageContract(any(),any())
    }

    @Test fun `resignation terminates current wage contract on requested date before deactivation`() {
        setup();val date=LocalDate.of(2024,2,1)
        val contract=com.worktime.domain.model.WageContract(worker=worker,hourlyWage=java.math.BigDecimal.TEN,startDate=date.minusMonths(1))
        whenever(wages.getCurrentWageContract(worker)).thenReturn(WageContractResponse.from(contract))
        val result=service.resignWorker(worker.id,date)
        assertFalse(result.active);verify(wages).terminateWageContract(contract.id,date);verify(workers).save(worker)
    }
}
