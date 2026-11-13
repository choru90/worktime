package com.worktime.application.service

import com.worktime.application.port.output.*
import com.worktime.adapter.input.web.dto.schedule.*
import com.worktime.domain.model.*
import com.worktime.domain.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.*
import java.util.UUID

class ScheduleBoundaryTest {
    private val schedules=mock<ScheduleRepository>()
    private val workers=mock<WorkerRepository>()
    private val owners=mock<OwnerRepository>()
    private val notifications=mock<NotificationService>()
    private val service=ScheduleService(schedules,workers,owners,notifications)
    private val user=User(email="o@test",password="pw",name="O",role=UserRole.OWNER)
    private val owner=Owner(user=user)
    private val employee=User(email="w@test",password="pw",name="W",role=UserRole.WORKER)
    private val worker=Worker(name="Worker",phoneNumber="000",owner=owner,userAccount=employee)
    private val start=OffsetDateTime.parse("2024-02-01T00:00:00Z")
    private fun schedule(status: ScheduleStatus=ScheduleStatus.REQUESTED_BY_WORKER)=Schedule(worker=worker,startAtUtc=start,endAtUtc=start.plusHours(1),timezone="Asia/Seoul",status=status)
    private fun setup(target: Schedule) {
        whenever(schedules.findById(target.id)).thenReturn(target)
        whenever(workers.findById(worker.id)).thenReturn(worker)
        whenever(schedules.save(any())).thenAnswer { it.arguments[0] as Schedule }
    }
    @Test fun `create proposal notifies linked worker and worker request does not`() {
        val target=schedule();setup(target)
        for(type in ScheduleRequestType.values()) {
            val response=service.createSchedule(ScheduleCreateRequest(worker.id,start,start.plusHours(1),"Asia/Seoul",type))
            assertEquals(if(type==ScheduleRequestType.OWNER_PROPOSAL) ScheduleStatus.PROPOSED_BY_OWNER else ScheduleStatus.REQUESTED_BY_WORKER,response.status)
            assertEquals(worker.id,response.workerId);assertEquals(start,response.startAtUtc)
            if(type==ScheduleRequestType.OWNER_PROPOSAL) verify(notifications).createNotification(eq(employee.id),eq(NotificationType.SCHEDULE_PROPOSED),any(),any(),eq(response.id))
        }
        verify(notifications,times(1)).createNotification(any(),any(),any(),any(),any())
    }
    @Test fun `unlinked worker receives no proposal notification`() {
        setup(schedule());worker.userAccount=null
        assertEquals(ScheduleStatus.PROPOSED_BY_OWNER,service.createSchedule(ScheduleCreateRequest(worker.id,start,start.plusHours(1),"UTC",ScheduleRequestType.OWNER_PROPOSAL)).status)
        verifyNoInteractions(notifications)
    }
    @Test fun `creation conflict describes overlapping time and never saves`() {
        val target=schedule(ScheduleStatus.CONFIRMED);setup(target)
        whenever(schedules.findConfirmedSchedulesOverlapping(worker,start,start.plusHours(1))).thenReturn(listOf(target))
        val error=assertThrows<ScheduleConflictException> { service.createSchedule(ScheduleCreateRequest(worker.id,start,start.plusHours(1),"UTC",ScheduleRequestType.WORKER_REQUEST)) }
        assertTrue(error.message!!.contains(start.toString()));verify(schedules,never()).save(any());verifyNoInteractions(notifications)
    }
    @Test fun `update excludes itself but rejects other overlapping confirmed schedule`() {
        val target=schedule();setup(target)
        whenever(schedules.findConfirmedSchedulesOverlapping(worker,start,start.plusHours(1))).thenReturn(listOf(target))
        val response=service.updateScheduleTime(target.id,ScheduleUpdateRequest(start,start.plusHours(1),"UTC"))
        assertEquals("UTC",response.timezone);assertEquals(start.plusHours(1),response.endAtUtc)
        val other=schedule(ScheduleStatus.CONFIRMED)
        whenever(schedules.findConfirmedSchedulesOverlapping(worker,start,start.plusHours(1))).thenReturn(listOf(target,other))
        assertThrows<ScheduleConflictException> { service.updateScheduleTime(target.id,ScheduleUpdateRequest(start,start.plusHours(1),"UTC")) }
        verify(schedules,times(1)).save(target)
    }
    @Test fun `confirmation notifies linked worker and prevents conflicting approval`() {
        val target=schedule();setup(target)
        whenever(schedules.findConfirmedSchedulesOverlapping(worker,start,start.plusHours(1))).thenReturn(listOf(target))
        val result=service.confirmSchedule(target.id)
        assertEquals(ScheduleStatus.CONFIRMED,result.status)
        verify(notifications).createNotification(eq(employee.id),eq(NotificationType.SCHEDULE_CONFIRMED),any(),any(),eq(target.id))
        target.status=ScheduleStatus.PROPOSED_BY_OWNER
        whenever(schedules.findConfirmedSchedulesOverlapping(worker,start,start.plusHours(1))).thenReturn(listOf(schedule(ScheduleStatus.CONFIRMED)))
        assertThrows<ScheduleConflictException> { service.confirmSchedule(target.id) }
        assertEquals(ScheduleStatus.PROPOSED_BY_OWNER,target.status)
    }
    @Test fun `all invalid schedule states fail before persistence`() {
        val target=schedule();setup(target)
        for(status in ScheduleStatus.values().filter { it !in listOf(ScheduleStatus.REQUESTED_BY_WORKER,ScheduleStatus.PROPOSED_BY_OWNER) }) {
            target.status=status
            assertThrows<InvalidScheduleStateException> { service.confirmSchedule(target.id) }
            assertThrows<InvalidScheduleStateException> { service.rejectSchedule(target.id) }
        }
        for(status in ScheduleStatus.values().filter { it!=ScheduleStatus.CONFIRMED }) {
            target.status=status;assertThrows<InvalidScheduleStateException> { service.cancelSchedule(target.id) }
        }
        verify(schedules,never()).save(any())
    }
    @Test fun `rejection notifies linked worker and cancellation persists terminal state`() {
        val target=schedule();setup(target)
        assertEquals(ScheduleStatus.REJECTED,service.rejectSchedule(target.id).status)
        verify(notifications).createNotification(eq(employee.id),eq(NotificationType.SCHEDULE_REJECTED),any(),any(),eq(target.id))
        target.status=ScheduleStatus.CONFIRMED
        assertEquals(ScheduleStatus.CANCELED,service.cancelSchedule(target.id).status)
    }
    @Test fun `missing schedule is rejected by every operation`() {
        val id=UUID.randomUUID()
        assertThrows<ResourceNotFoundException> { service.getScheduleById(id) }
        assertThrows<ResourceNotFoundException> { service.confirmSchedule(id) }
        assertThrows<ResourceNotFoundException> { service.rejectSchedule(id) }
        assertThrows<ResourceNotFoundException> { service.cancelSchedule(id) }
        assertThrows<ResourceNotFoundException> { service.updateScheduleTime(id,ScheduleUpdateRequest(start,start.plusHours(1),"UTC")) }
        assertThrows<ResourceNotFoundException> { service.createSchedule(ScheduleCreateRequest(id,start,start.plusHours(1),"UTC",ScheduleRequestType.WORKER_REQUEST)) }
    }
    @Test fun `listing binds owner and filters status and requires full range`() {
        val target=schedule();setup(target);whenever(owners.findByUserId(user.id)).thenReturn(owner)
        whenever(schedules.findByWorkerAndTimeRange(worker,start,start.plusDays(1))).thenReturn(listOf(target,schedule(ScheduleStatus.CANCELED)))
        whenever(schedules.findByOwnerAndTimeRange(owner.id,start,start.plusDays(1))).thenReturn(listOf(target))
        assertEquals(listOf(target.id),service.getAllSchedules(user.id,start,start.plusDays(1),worker.id,ScheduleStatus.REQUESTED_BY_WORKER).map { it.id })
        assertEquals(listOf(target.id),service.getAllSchedules(user.id,start,start.plusDays(1),null,null).map { it.id })
        assertEquals(2,service.getSchedulesByWorker(worker.id,start,start.plusDays(1)).size)
        for((from,to) in listOf(null to null,start to null,null to start)) {
            assertTrue(service.getSchedulesByWorker(worker.id,from,to).isEmpty())
            assertTrue(service.getAllSchedules(user.id,from,to,worker.id,null).isEmpty())
            assertTrue(service.getAllSchedules(user.id,from,to,null,null).isEmpty())
        }
        assertThrows<ResourceNotFoundException> { service.getAllSchedules(UUID.randomUUID(),start,start,null,null) }
        assertThrows<ResourceNotFoundException> { service.getAllSchedules(user.id,start,start,UUID.randomUUID(),null) }
        assertThrows<ResourceNotFoundException> { service.getSchedulesByWorker(UUID.randomUUID(),null,null) }
        val foreign=Owner(user=user);whenever(owners.findByUserId(user.id)).thenReturn(foreign)
        assertThrows<BusinessException> { service.getAllSchedules(user.id,start,start,worker.id,null) }
    }
    @Test fun `monthly generation includes leap-day and effective boundaries but skips inactive and conflicts`() {
        setup(schedule());val store=Store(owner=owner,name="Cafe")
        val shift=RegularShift(store=store,worker=worker,dayOfWeek=DayOfWeek.THURSDAY,startTime=LocalTime.of(9,0),endTime=LocalTime.of(10,0),effectiveFrom=LocalDate.of(2024,2,8),effectiveUntil=LocalDate.of(2024,2,29))
        val inactive=RegularShift(store=store,worker=worker,dayOfWeek=DayOfWeek.THURSDAY,startTime=LocalTime.of(11,0),endTime=LocalTime.of(12,0),active=false)
        whenever(schedules.findConfirmedSchedulesOverlapping(eq(worker),any(),any())).thenAnswer { invocation ->
            if((invocation.arguments[1] as OffsetDateTime).dayOfMonth==15) listOf(schedule(ScheduleStatus.CONFIRMED)) else emptyList<Schedule>()
        }
        val result=service.generateMonthlySchedules(store.id,2024,2,listOf(shift,inactive))
        assertEquals(listOf(8,22,29),result.map { it.startAtUtc.dayOfMonth })
        assertTrue(result.all { it.startAtUtc.hour==0 && it.endAtUtc.hour==1 && it.status==ScheduleStatus.CONFIRMED })
        verify(workers,times(1)).lockForWrite(worker)
        shift.effectiveFrom=null;shift.effectiveUntil=null
        assertEquals(4,service.generateMonthlySchedules(store.id,2024,2,listOf(shift)).size)
        whenever(workers.findById(worker.id)).thenReturn(null)
        assertThrows<ResourceNotFoundException> { service.generateMonthlySchedules(store.id,2024,2,listOf(shift)) }
    }
}
