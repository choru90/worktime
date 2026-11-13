package com.worktime.application.service

import com.worktime.application.port.output.*
import com.worktime.adapter.input.web.dto.worklog.*
import com.worktime.domain.model.*
import com.worktime.domain.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.OffsetDateTime
import java.util.UUID

class WorkLogRevisionBoundaryTest {
    private val logs=mock<WorkLogRepository>()
    private val workers=mock<WorkerRepository>()
    private val schedules=mock<ScheduleRepository>()
    private val service=WorkLogService(logs,workers,schedules)
    private val owner=Owner(user=User(email="o@test",password="pw",name="O",role=UserRole.OWNER))
    private val worker=Worker(name="Worker",phoneNumber="000",owner=owner)
    private val start=OffsetDateTime.parse("2024-01-01T09:00:00Z")
    private val log=WorkLog(worker=worker,clockInAt=start,clockOutAt=start.plusHours(1),status=WorkLogStatus.COMPLETED)
    private fun setup() {
        whenever(workers.findById(worker.id)).thenReturn(worker)
        whenever(logs.findById(log.id)).thenReturn(log)
        whenever(logs.save(any())).thenAnswer { it.arguments[0] as WorkLog }
    }
    private fun request()=CorrectWorkLogRequest(start.plusMinutes(1),start.plusHours(2),"  corrected time  ",0,WorkLogStatus.COMPLETED)
    private fun schedule(at: OffsetDateTime=start,status: ScheduleStatus=ScheduleStatus.CONFIRMED)=Schedule(worker=worker,startAtUtc=at,endAtUtc=at.plusHours(1),timezone="UTC",status=status)
    @Test fun `correction appends immutable prior revision clears rejection and increments count`() {
        setup();log.status=WorkLogStatus.REJECTED;log.rejectionReason="old rejection"
        val response=service.correctWorkLog(worker.id,log.id,request().copy(expectedStatus=WorkLogStatus.REJECTED))
        assertEquals(WorkLogStatus.COMPLETED,response.status);assertEquals(1,log.correctionCount)
        assertEquals(start.plusMinutes(1),log.clockInAt);assertEquals(start.plusHours(2),log.clockOutAt)
        assertEquals("corrected time",log.correctionReason);assertNull(log.rejectionReason)
        val revision=log.corrections.single()
        assertEquals(start,revision.previousIn);assertEquals(start.plusHours(1),revision.previousOut)
        assertEquals(WorkLogStatus.REJECTED,revision.previousStatus);assertEquals("old rejection",revision.previousRejectionReason)
        assertEquals(1,revision.revision);assertEquals("corrected time",revision.reason)
        verify(workers).lockForWrite(worker);verify(logs).refreshForWrite(log);verify(logs).save(log)
    }
    @Test fun `correction rejects missing foreign stale and immutable records before save`() {
        setup()
        assertThrows<ResourceNotFoundException> { service.correctWorkLog(worker.id,UUID.randomUUID(),request()) }
        assertThrows<ResourceNotFoundException> { service.correctWorkLog(UUID.randomUUID(),log.id,request()) }
        assertThrows<WorkLogConflictException> { service.correctWorkLog(worker.id,log.id,request().copy(expectedCorrectionCount=1)) }
        assertThrows<WorkLogConflictException> { service.correctWorkLog(worker.id,log.id,request().copy(expectedStatus=WorkLogStatus.REJECTED)) }
        for(status in listOf(WorkLogStatus.CONFIRMED,WorkLogStatus.NO_SHOW,WorkLogStatus.MANUAL_ADJUSTED)) {
            log.status=status
            assertThrows<InvalidWorkLogStateException> { service.correctWorkLog(worker.id,log.id,request().copy(expectedStatus=status)) }
        }
        assertTrue(log.corrections.isEmpty());assertEquals(0,log.correctionCount);verify(logs,never()).save(any())
    }
    @Test fun `correction rejects reversed equal future and overlapping times without mutation`() {
        setup()
        for(end in listOf(start,start.minusSeconds(1),OffsetDateTime.now().plusDays(1))) {
            assertThrows<InvalidWorkLogStateException> { service.correctWorkLog(worker.id,log.id,request().copy(clockInAt=start,clockOutAt=end)) }
        }
        whenever(logs.hasOverlap(worker,log.id,request().clockInAt,request().clockOutAt)).thenReturn(true)
        assertThrows<WorkLogConflictException> { service.correctWorkLog(worker.id,log.id,request()) }
        assertEquals(start,log.clockInAt);assertEquals(0,log.correctionCount);assertTrue(log.corrections.isEmpty());verify(logs,never()).save(any())
    }
    @Test fun `ongoing shift correction completes previous null clock-out with audit history`() {
        setup();log.status=WorkLogStatus.ONGOING;log.clockOutAt=null
        val result=service.correctWorkLog(worker.id,log.id,request().copy(expectedStatus=WorkLogStatus.ONGOING))
        assertEquals(WorkLogStatus.COMPLETED,result.status);assertNull(log.corrections.single().previousOut)
    }
    @Test fun `review rejects stale revision and invalid state without saving`() {
        setup();log.correctionCount=1
        assertThrows<WorkLogConflictException> { service.confirmWorkLog(log.id,0) }
        assertThrows<WorkLogConflictException> { service.rejectWorkLog(log.id,"reason",0) }
        log.status=WorkLogStatus.CONFIRMED
        assertThrows<InvalidWorkLogStateException> { service.confirmWorkLog(log.id,1) }
        assertThrows<InvalidWorkLogStateException> { service.rejectWorkLog(log.id,"reason",1) }
        assertThrows<ResourceNotFoundException> { service.confirmWorkLog(UUID.randomUUID(),0) }
        verify(logs,never()).save(any())
    }
    @Test fun `rejection reason is nullable trimmed and limited to 500 characters`() {
        setup()
        for(reason in listOf(null,"   denied   ","x".repeat(501))) {
            log.status=WorkLogStatus.COMPLETED
            val result=service.rejectWorkLog(log.id,reason,0)
            assertEquals(WorkLogStatus.REJECTED,result.status);assertEquals(reason?.trim()?.take(500),log.rejectionReason)
        }
    }
    @Test fun `clock-in binds schedule to worker and prevents duplicate schedule record`() {
        setup();val schedule=schedule();whenever(schedules.findById(schedule.id)).thenReturn(schedule)
        whenever(logs.findByScheduleIds(listOf(schedule.id))).thenReturn(listOf(log))
        assertThrows<WorkLogConflictException> { service.clockIn(worker.id,ClockInRequest(scheduleId=schedule.id,clockInAt=start)) }
        assertThrows<ResourceNotFoundException> { service.clockIn(worker.id,ClockInRequest(scheduleId=UUID.randomUUID(),clockInAt=start)) }
        val other=Worker(name="Other",phoneNumber="000",owner=owner)
        val foreign=Schedule(worker=other,startAtUtc=start,endAtUtc=start.plusHours(1),timezone="UTC")
        whenever(schedules.findById(foreign.id)).thenReturn(foreign)
        assertThrows<ResourceNotFoundException> { service.clockIn(worker.id,ClockInRequest(scheduleId=foreign.id,clockInAt=start)) }
        verify(logs,never()).save(any())
    }
    @Test fun `listing creates no-show only for elapsed eligible schedules without a log`() {
        setup();val existing=schedule();val elapsed=schedule(start.minusDays(1));val future=schedule(OffsetDateTime.now().plusDays(1))
        val canceled=schedule(status=ScheduleStatus.CANCELED);val rejected=schedule(status=ScheduleStatus.REJECTED)
        val linked=WorkLog(worker=worker,schedule=existing,clockInAt=start,clockOutAt=start.plusHours(1),status=WorkLogStatus.COMPLETED)
        whenever(logs.findByOwnerAndTimeRange(owner.id,start.minusDays(2),start.plusDays(2))).thenReturn(listOf(linked,log))
        whenever(schedules.findByOwnerAndTimeRange(owner.id,start.minusDays(2),start.plusDays(2))).thenReturn(listOf(existing,elapsed,future,canceled,rejected))
        val result=service.getAllWorkLogs(start.minusDays(2),start.plusDays(2),null,null,owner.id)
        assertEquals(3,result.size);assertEquals(elapsed.id,result.single { it.status==WorkLogStatus.NO_SHOW }.scheduleId)
        assertEquals(result.map { it.clockInAt }.sortedDescending(),result.map { it.clockInAt })
        val filtered=service.getAllWorkLogs(start.minusDays(2),start.plusDays(2),null,WorkLogStatus.NO_SHOW,owner.id)
        assertEquals(listOf(elapsed.id),filtered.map { it.id })
    }
    @Test fun `listing enforces owner boundary and requires both time limits`() {
        setup()
        for((from,to) in listOf(null to null,start to null,null to start)) {
            assertTrue(service.getAllWorkLogs(from,to,null,null,owner.id).isEmpty())
            assertTrue(service.getAllWorkLogs(from,to,worker.id,null,owner.id).isEmpty())
            assertTrue(service.getWorkLogsByWorker(worker.id,from,to).isEmpty())
        }
        assertThrows<ResourceNotFoundException> { service.getAllWorkLogs(start,start.plusDays(1),worker.id,null,UUID.randomUUID()) }
        assertThrows<ResourceNotFoundException> { service.getAllWorkLogs(start,start.plusDays(1),UUID.randomUUID(),null,owner.id) }
        verify(logs,never()).findByOwnerAndTimeRange(any(),any(),any())
    }

    @Test fun `manual log requires existing owned confirmed schedule and prevents duplicates`() {
        setup();val schedule=schedule();whenever(schedules.findById(schedule.id)).thenReturn(schedule)
        val request=ManualWorkLogRequest(start,start.plusHours(1))
        assertThrows<ResourceNotFoundException> { service.createManualWorkLog(UUID.randomUUID(),schedule.id,request) }
        assertThrows<ResourceNotFoundException> { service.createManualWorkLog(worker.id,UUID.randomUUID(),request) }
        schedule.status=ScheduleStatus.REQUESTED_BY_WORKER
        assertThrows<BusinessException> { service.createManualWorkLog(worker.id,schedule.id,request) }
        schedule.status=ScheduleStatus.CONFIRMED
        whenever(logs.findByScheduleIds(listOf(schedule.id))).thenReturn(listOf(log))
        assertThrows<WorkLogConflictException> { service.createManualWorkLog(worker.id,schedule.id,request) }
        whenever(logs.findByScheduleIds(listOf(schedule.id))).thenReturn(emptyList())
        val result=service.createManualWorkLog(worker.id,schedule.id,request)
        assertEquals(worker.id,result.workerId);assertEquals(schedule.id,result.scheduleId)
        assertEquals(WorkLogStatus.COMPLETED,result.status);assertEquals(60L,result.durationMinutes)
        val foreign=Schedule(worker=Worker(name="Foreign",phoneNumber="001",owner=owner),startAtUtc=start,endAtUtc=start.plusHours(1),timezone="UTC",status=ScheduleStatus.CONFIRMED)
        whenever(schedules.findById(foreign.id)).thenReturn(foreign)
        assertThrows<BusinessException> { service.createManualWorkLog(worker.id,foreign.id,request) }
    }
    @Test fun `missing worker and log fail read and clock operations without writes`() {
        val missing=UUID.randomUUID()
        assertThrows<ResourceNotFoundException> { service.clockOut(missing,ClockOutRequest(start)) }
        assertThrows<ResourceNotFoundException> { service.getWorkLogById(missing) }
        assertThrows<ResourceNotFoundException> { service.getWorkLogsByWorker(missing,null,null) }
        assertThrows<ResourceNotFoundException> { service.getCurrentOngoingWorkLog(missing) }
        setup();assertNull(service.getCurrentOngoingWorkLog(worker.id))
        whenever(logs.findCurrentOngoingWorkLog(worker)).thenReturn(log)
        assertEquals(log.id,service.getCurrentOngoingWorkLog(worker.id)!!.id)
        assertEquals(log.id,service.getWorkLogById(log.id).id)
        verify(logs,never()).save(any())
    }

    @Test fun `owner filtering by worker returns actual logs and synthetic missing checkins`() {
        setup();val pending=schedule(start.minusDays(1),ScheduleStatus.PROPOSED_BY_OWNER)
        whenever(logs.findByWorkerAndTimeRange(worker,start.minusDays(2),start.plusDays(2))).thenReturn(listOf(log))
        whenever(schedules.findByWorkerAndTimeRange(worker,start.minusDays(2),start.plusDays(2))).thenReturn(listOf(pending))
        val result=service.getAllWorkLogs(start.minusDays(2),start.plusDays(2),worker.id,null,owner.id)
        assertEquals(listOf(log.id,pending.id),result.map { it.id });assertEquals(WorkLogStatus.NO_SHOW,result.last().status)
    }

    @Test fun `review input port defaults apply revision zero to current completed log`() {
        setup();val api: com.worktime.application.port.input.WorkLogUseCase=service
        assertEquals(WorkLogStatus.CONFIRMED,api.confirmWorkLog(log.id).status)
        log.status=WorkLogStatus.COMPLETED
        assertEquals(WorkLogStatus.REJECTED,api.rejectWorkLog(log.id,"reason").status)
        assertEquals("reason",log.rejectionReason)
    }

    @Test fun `clock-out equal to clock-in fails without changing ongoing shift`() {
        setup();log.status=WorkLogStatus.ONGOING;log.clockOutAt=null
        whenever(logs.findCurrentOngoingWorkLog(worker)).thenReturn(log)
        val error=assertThrows<InvalidWorkLogStateException> { service.clockOut(worker.id,ClockOutRequest(start)) }
        assertTrue(error.message!!.contains("after clock-in"));assertEquals(WorkLogStatus.ONGOING,log.status);assertNull(log.clockOutAt)
        verify(logs,never()).save(any())
    }

    @Test fun `listing tolerates worker repository no longer finding worker at its second lookup`() {
        // Unit contract of the port's defensive fallback; this does not certify a JPA deletion race.
        whenever(workers.findById(worker.id)).thenReturn(worker,null)
        val result=service.getAllWorkLogs(start,start.plusDays(1),worker.id,null,owner.id)
        assertTrue(result.isEmpty())
        verify(logs).findByWorkerAndTimeRange(worker,start,start.plusDays(1))
        verifyNoInteractions(schedules)
    }
}
