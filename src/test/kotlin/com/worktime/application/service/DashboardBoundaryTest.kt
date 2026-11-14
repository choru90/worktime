package com.worktime.application.service

import com.worktime.application.port.output.*
import com.worktime.application.port.input.dto.*
import com.worktime.domain.model.*
import com.worktime.domain.util.DashboardDateRange
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.test.util.ReflectionTestUtils
import java.time.*
import java.math.BigDecimal
import java.util.UUID

class DashboardBoundaryTest {
    private val schedules=mock<ScheduleRepository>();private val logs=mock<WorkLogRepository>()
    private val workers=mock<WorkerRepository>();private val wages=mock<WageContractRepository>()
    private val service=DashboardReadService(schedules,logs,workers,wages)
    private val owner=Owner(user=User(email="o@test",password="pw",name="O",role=UserRole.OWNER))
    private val day=LocalDate.of(2024,2,1);private val start=OffsetDateTime.parse("2024-02-01T09:00:00Z")
    private fun worker(name: String)=Worker(name=name,phoneNumber="000",owner=owner)
    @Test fun `action filters include explicit pending type and omit excluded and empty type sets`() {
        val a=worker("Alpha");val z=worker("Zulu")
        val first=Schedule(worker=z,startAtUtc=start,endAtUtc=start.plusHours(1),timezone="UTC")
        val second=Schedule(worker=a,startAtUtc=start,endAtUtc=start.plusHours(1),timezone="UTC")
        val fixed=Instant.parse("2024-01-01T00:00:00Z")
        ReflectionTestUtils.setField(first,"createdAt",fixed);ReflectionTestUtils.setField(second,"createdAt",fixed)
        whenever(schedules.findByOwnerAndStatusIn(eq(owner.id),any())).thenReturn(listOf(first,second))
        val result=service.getDashboardActions(owner.id,null,1,listOf(DashboardActionType.PENDING_SCHEDULE_APPROVAL))
        assertEquals(2,result.totalCount);assertEquals("Alpha",result.items.single().meta.workerName)
        assertTrue(result.items.single().message.contains("18:00~19:00"))
        assertEquals(LocalDate.now().toString(),result.date)
        assertTrue(service.getDashboardActions(owner.id,day,5,emptyList()).items.isEmpty())
        val excluded=DashboardActionType.values().filter { it!=DashboardActionType.PENDING_SCHEDULE_APPROVAL }
        assertTrue(service.getDashboardActions(owner.id,day,5,excluded).items.isEmpty())
    }
    @Test fun `top workers tie-break by name for every sort and retain zero wages without active contract`() {
        val a=worker("Alpha");val z=worker("Zulu")
        val workLogs=listOf(z,a).map { WorkLog(worker=it,clockInAt=start,clockOutAt=start.plusHours(1),status=WorkLogStatus.CONFIRMED) }
        whenever(logs.findCompletedWorkLogsByOwnerAndTimeRange(eq(owner.id),any(),any())).thenReturn(workLogs)
        whenever(wages.findByWorkerIn(any())).thenReturn(listOf(WageContract(worker=a,hourlyWage=BigDecimal("10000"),startDate=day.plusDays(1))))
        for(sort in TopWorkerSort.values()) {
            val result=service.getTopWorkers(owner.id,day,day,5,sort)
            assertEquals(listOf("Alpha","Zulu"),result.items.map { it.worker.name })
            assertEquals(listOf(1,2),result.items.map { it.rank });assertTrue(result.items.all { it.stats.totalWorkMinutes==60L && it.stats.completedShifts==1 && it.stats.accruedWage==BigDecimal.ZERO })
            assertTrue(result.items[1].badges.isEmpty())
        }
    }
    @Test fun `summary handles absent wage contract and incomplete historical confirmed record as zero wage`() {
        val worker=worker("Historical")
        val incomplete=WorkLog(worker=worker,clockInAt=start,status=WorkLogStatus.CONFIRMED)
        whenever(logs.findByOwnerAndTimeRange(eq(owner.id),any(),any())).thenReturn(listOf(incomplete))
        whenever(wages.findByWorker(worker)).thenReturn(emptyList())
        val without=service.getDashboardSummary(owner.id,day)
        assertEquals(BigDecimal.ZERO.setScale(0),without.month.accruedWage);assertEquals(0,without.today.totalWorkMinutes)
        whenever(wages.findByWorker(worker)).thenReturn(listOf(WageContract(worker=worker,hourlyWage=BigDecimal.TEN,startDate=day)))
        assertEquals(BigDecimal.ZERO.setScale(0),service.getDashboardSummary(owner.id,day).month.accruedWage)
    }
    @Test fun `attendance excludes schedules touching outside daily range and handles unrelated orphan log`() {
        val worker=worker("Attendance");val range=DashboardDateRange.todayRange(day)
        val before=Schedule(worker=worker,startAtUtc=range.start.minusHours(1),endAtUtc=range.start,timezone="UTC")
        val after=Schedule(worker=worker,startAtUtc=range.endExclusive,endAtUtc=range.endExclusive.plusHours(1),timezone="UTC")
        val inside=Schedule(worker=worker,startAtUtc=range.start,endAtUtc=range.start.plusHours(1),timezone="UTC")
        whenever(schedules.findByOwnerAndStatusIn(eq(owner.id),any())).thenReturn(listOf(before,after,inside))
        whenever(logs.findByScheduleIds(any())).thenReturn(listOf(WorkLog(worker=worker,clockInAt=start)))
        val result=service.getAttendanceSummary(owner.id,day,false)
        assertEquals(1,result.summary.scheduledCount);assertEquals(0,result.summary.checkedInCount);assertEquals(1,result.summary.notCheckedInCount)
        assertEquals(inside.id,result.exceptions.single().scheduleId)
    }

    @Test fun `input port defaults provide full action list attendance and seven day top worker range`() {
        val api: com.worktime.application.port.input.DashboardReadUseCase=service
        assertEquals(0,api.getDashboardActions(owner.id).totalCount)
        assertEquals(0,api.getAttendanceSummary(owner.id,day).summary.scheduledCount)
        val top=api.getTopWorkers(owner.id,null,null)
        assertEquals(7L,top.range.days);assertTrue(top.items.isEmpty())
        assertEquals(LocalDate.now(ZoneId.of("Asia/Seoul")),top.range.to)
    }

    @Test fun `completed shift ranking prioritizes count over alphabetical worker name`() {
        val a=worker("Alpha");val z=worker("Zulu")
        val workLogs=listOf(WorkLog(worker=a,clockInAt=start,clockOutAt=start.plusHours(1),status=WorkLogStatus.COMPLETED),
            WorkLog(worker=z,clockInAt=start,clockOutAt=start.plusHours(1),status=WorkLogStatus.COMPLETED),
            WorkLog(worker=z,clockInAt=start.plusDays(1),clockOutAt=start.plusDays(1).plusHours(1),status=WorkLogStatus.COMPLETED))
        whenever(logs.findCompletedWorkLogsByOwnerAndTimeRange(eq(owner.id),any(),any())).thenReturn(workLogs)
        val result=service.getTopWorkers(owner.id,day,day.plusDays(1),5,TopWorkerSort.SHIFTS_DESC)
        assertEquals(listOf("Zulu","Alpha"),result.items.map { it.worker.name })
        assertEquals(listOf(2,1),result.items.map { it.stats.completedShifts });assertEquals(listOf(1,2),result.items.map { it.rank })
        assertEquals(listOf(120L,60L),result.items.map { it.stats.totalWorkMinutes })
    }
}
