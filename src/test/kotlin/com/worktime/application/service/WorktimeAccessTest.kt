package com.worktime.application.service

import com.worktime.application.port.output.*
import com.worktime.adapter.input.web.dto.schedule.*
import com.worktime.domain.model.*
import com.worktime.domain.exception.ResourceNotFoundException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.access.AccessDeniedException
import java.time.OffsetDateTime
import java.util.UUID

class WorktimeAccessTest {
    private val workers = mock<WorkerRepository>()
    private val owners = mock<OwnerRepository>()
    private val logs = mock<WorkLogRepository>()
    private val schedules = mock<ScheduleRepository>()
    private val stores=mock<com.worktime.adapter.output.persistence.StoreJpaRepository>()
    private val shifts=mock<com.worktime.adapter.output.persistence.RegularShiftJpaRepository>()
    private val contracts=mock<WageContractRepository>()
    private val access = WorktimeAccess(workers, owners, logs, schedules, stores, shifts, contracts)
    private val ownerUser = User(email="owner@test", password="pw", name="Owner", role=UserRole.OWNER)
    private val owner = Owner(user=ownerUser)
    private val account = User(email="worker@test", password="pw", name="Worker", role=UserRole.WORKER)
    private val worker = Worker(name="Worker", phoneNumber="000", owner=owner, userAccount=account)
    private val start = OffsetDateTime.parse("2025-01-01T09:00:00Z")

    @AfterEach fun clearContext() = SecurityContextHolder.clearContext()
    private fun login(id: UUID, vararg roles: String) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            id, null, roles.map { SimpleGrantedAuthority("ROLE_$it") })
    }
    private fun target() { whenever(workers.findById(worker.id)).thenReturn(worker) }

    @Test fun `authentication and owner role are required`() {
        assertEquals("Authentication required", assertThrows<AccessDeniedException> { access.ownerId() }.message)
        login(account.id, "WORKER")
        assertEquals("Owner role required", assertThrows<AccessDeniedException> { access.ownerId() }.message)
    }
    @Test fun `owner account must exist`() {
        login(ownerUser.id, "OWNER")
        assertThrows<ResourceNotFoundException> { access.ownerId() }
        whenever(owners.findByUserId(ownerUser.id)).thenReturn(owner)
        assertEquals(owner.id, access.ownerId())
    }
    @Test fun `worker ownership cannot be crossed by any role`() {
        target()
        login(ownerUser.id, "OWNER"); assertTrue(access.worker(worker.id))
        login(account.id, "WORKER"); assertTrue(access.worker(worker.id))
        login(UUID.randomUUID(), "OWNER"); assertThrows<ResourceNotFoundException> { access.worker(worker.id) }
        login(UUID.randomUUID(), "WORKER"); assertThrows<ResourceNotFoundException> { access.worker(worker.id) }
        login(account.id, "AUDITOR"); assertThrows<ResourceNotFoundException> { access.worker(worker.id) }
        login(account.id); assertThrows<ResourceNotFoundException> { access.worker(worker.id) }
        login(account.id, "WORKER"); worker.userAccount=null
        assertThrows<ResourceNotFoundException> { access.worker(worker.id) }
        assertThrows<ResourceNotFoundException> { access.worker(UUID.randomUUID()) }
    }
    @Test fun `logs and schedules delegate ownership and hide missing resources`() {
        target(); login(ownerUser.id, "OWNER")
        val log=WorkLog(worker=worker, clockInAt=start)
        val schedule=Schedule(worker=worker,startAtUtc=start,endAtUtc=start.plusHours(1),timezone="UTC")
        whenever(logs.findById(log.id)).thenReturn(log)
        whenever(schedules.findById(schedule.id)).thenReturn(schedule)
        assertTrue(access.log(log.id)); assertTrue(access.schedule(schedule.id))
        assertThrows<ResourceNotFoundException> { access.log(UUID.randomUUID()) }
        assertThrows<ResourceNotFoundException> { access.schedule(UUID.randomUUID()) }
        login(UUID.randomUUID(), "OWNER")
        assertThrows<ResourceNotFoundException> { access.log(log.id) }
        assertThrows<ResourceNotFoundException> { access.schedule(schedule.id) }
    }
    @Test fun `each side may respond only to the other side pending schedule`() {
        target()
        val schedule=Schedule(worker=worker,startAtUtc=start,endAtUtc=start.plusHours(1),timezone="UTC")
        whenever(schedules.findById(schedule.id)).thenReturn(schedule)
        for (status in ScheduleStatus.values()) {
            schedule.status=status
            login(ownerUser.id,"OWNER")
            if (status==ScheduleStatus.REQUESTED_BY_WORKER) assertTrue(access.respondSchedule(schedule.id))
            else assertThrows<AccessDeniedException> { access.respondSchedule(schedule.id) }
            login(account.id,"WORKER")
            if (status==ScheduleStatus.PROPOSED_BY_OWNER) assertTrue(access.respondSchedule(schedule.id))
            else assertThrows<AccessDeniedException> { access.respondSchedule(schedule.id) }
        }
        assertThrows<ResourceNotFoundException> { access.respondSchedule(UUID.randomUUID()) }
    }
    @Test fun `schedule creation binds path worker and request role to authenticated user`() {
        target()
        for ((id,role,expected) in listOf(Triple(ownerUser.id,"OWNER",ScheduleRequestType.OWNER_PROPOSAL),
            Triple(account.id,"WORKER",ScheduleRequestType.WORKER_REQUEST))) {
            login(id,role)
            for (type in ScheduleRequestType.values()) {
                val request=ScheduleCreateRequest(worker.id,start,start.plusHours(1),"UTC",type)
                if(type==expected) assertTrue(access.createSchedule(worker.id,request))
                else assertThrows<AccessDeniedException> { access.createSchedule(worker.id,request) }
                assertThrows<ResourceNotFoundException> { access.createSchedule(worker.id,request.copy(workerId=UUID.randomUUID())) }
            }
        }
    }

    @Test fun `store and shift checks reject missing targets non-owner roles and foreign owners`() {
        val store=Store(owner=owner,name="Cafe")
        val shift=RegularShift(store=store,worker=worker,dayOfWeek=java.time.DayOfWeek.MONDAY,startTime=java.time.LocalTime.of(9,0),endTime=java.time.LocalTime.of(10,0))
        whenever(stores.findById(store.id)).thenReturn(java.util.Optional.of(store))
        whenever(shifts.findById(shift.id)).thenReturn(java.util.Optional.of(shift))
        login(ownerUser.id,"OWNER");assertTrue(access.store(store.id));assertTrue(access.shift(shift.id))
        assertThrows<ResourceNotFoundException> { access.store(UUID.randomUUID()) }
        assertThrows<ResourceNotFoundException> { access.shift(UUID.randomUUID()) }
        login(account.id,"WORKER");assertThrows<ResourceNotFoundException> { access.store(store.id) }
        login(UUID.randomUUID(),"OWNER");assertThrows<ResourceNotFoundException> { access.store(store.id) }
        assertThrows<ResourceNotFoundException> { access.shift(shift.id) }
    }
    @Test fun `contract guard binds contract identifier to permitted worker`() {
        target();login(ownerUser.id,"OWNER")
        val contract=WageContract(worker=worker,hourlyWage=java.math.BigDecimal.TEN,startDate=java.time.LocalDate.of(2024,1,1))
        whenever(contracts.findById(contract.id)).thenReturn(contract)
        assertTrue(access.contract(worker.id,contract.id))
        assertThrows<ResourceNotFoundException> { access.contract(worker.id,UUID.randomUUID()) }
        val foreign=WageContract(worker=Worker(name="Foreign",phoneNumber="001",owner=owner),hourlyWage=java.math.BigDecimal.TEN,startDate=java.time.LocalDate.of(2024,1,1))
        whenever(contracts.findById(foreign.id)).thenReturn(foreign)
        assertThrows<ResourceNotFoundException> { access.contract(worker.id,foreign.id) }
    }
}
