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
    private val access = WorktimeAccess(workers, owners, logs, schedules)
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
}
