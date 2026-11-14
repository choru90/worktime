package com.worktime.application.service

import com.worktime.application.port.output.*
import com.worktime.application.port.input.WageContractUseCase
import com.worktime.adapter.input.web.dto.wage.WageInfoResponse
import com.worktime.domain.model.*
import com.worktime.domain.exception.BusinessException
import com.worktime.domain.exception.ResourceNotFoundException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class PayrollCalculationBoundaryTest {
    private val logs=mock<WorkLogRepository>()
    private val workers=mock<WorkerRepository>()
    private val owners=mock<OwnerRepository>()
    private val wages=mock<WageContractUseCase>()
    private val service=PayrollService(logs,workers,owners,wages)
    private val owner=Owner(user=User(email="o@test",password="pw",name="Owner",role=UserRole.OWNER))
    private val worker=Worker(name="Worker",phoneNumber="000",owner=owner)
    private val day=LocalDate.of(2024,2,29)
    private val start=OffsetDateTime.parse("2024-02-29T09:00:00Z")
    private fun setup() { whenever(workers.findById(worker.id)).thenReturn(worker) }
    private fun log(at: OffsetDateTime=start,minutes: Long?=60)=WorkLog(worker=worker,clockInAt=at,
        clockOutAt=minutes?.let { at.plusMinutes(it) },status=WorkLogStatus.CONFIRMED)
    private fun wage(date: LocalDate, amount: String?) {
        whenever(wages.getWageOnDate(worker.id,date)).thenReturn(WageInfoResponse(date,amount?.let(::BigDecimal),amount!=null))
    }
    @Test fun `payroll rounds each shift half up and looks up its UTC work date`() {
        setup()
        val first=log(minutes=1)
        val second=log(OffsetDateTime.parse("2024-03-01T00:30:00+09:00"),30)
        whenever(logs.findConfirmedWorkLogsInRange(any(),any(),any())).thenReturn(listOf(first,second))
        wage(day,"10005"); wage(day.plusDays(1),"10001")
        val result=service.calculatePayroll(worker.id,day,day.plusDays(1))
        assertEquals(BigDecimal("5170"),result.totalPay)
        assertEquals(listOf(BigDecimal("167"),BigDecimal("5003")),result.wageDetails.map { it.pay })
        assertEquals(listOf(day,day),result.wageDetails.map { it.date })
        assertEquals(31.0/60,result.totalHours,0.0000001)
        verify(logs).findConfirmedWorkLogsInRange(worker,day.atStartOfDay().atOffset(ZoneOffset.UTC),day.plusDays(2).atStartOfDay().atOffset(ZoneOffset.UTC))
    }
    @Test fun `incomplete confirmed shift fails instead of silently reducing pay`() {
        setup(); val incomplete=log(minutes=null)
        whenever(logs.findConfirmedWorkLogsInRange(any(),any(),any())).thenReturn(listOf(incomplete))
        assertEquals("Cannot calculate pay for incomplete work log: ${incomplete.id}",
            assertThrows<BusinessException> { service.calculatePayroll(worker.id,day,day) }.message)
        verifyNoInteractions(wages)
    }
    @Test fun `daily payroll aggregates shifts sorts days and treats absent contract as zero`() {
        setup()
        whenever(logs.findConfirmedWorkLogsInRange(any(),any(),any())).thenReturn(listOf(
            log(start,30),log(start.minusDays(1),15),log(start.plusHours(2),31),log(start.minusDays(1).plusHours(1),null)))
        wage(day,"10005"); wage(day.minusDays(1),null)
        val result=service.getWorkerMonthlyPayrollDetail(worker.id,2024,2)
        assertEquals(worker.id,result.workerId);assertEquals("Worker",result.workerName)
        assertEquals(2024,result.year);assertEquals(2,result.month)
        assertEquals(76L,result.totalWorkMinutes);assertEquals(76.0/60,result.totalWorkHours)
        assertEquals(BigDecimal("10172"),result.totalAmount)
        assertEquals(listOf("2024-02-28","2024-02-29"),result.dailyDetails.map { it.date })
        assertEquals(listOf(15L,61L),result.dailyDetails.map { it.workMinutes })
        assertEquals(BigDecimal.ZERO,result.dailyDetails[0].hourlyWage)
        assertEquals(BigDecimal.ZERO,result.dailyDetails[0].amount)
        verify(logs).findConfirmedWorkLogsInRange(worker,OffsetDateTime.parse("2024-02-01T00:00:00Z"),OffsetDateTime.parse("2024-03-01T00:00:00Z"))
    }
    @Test fun `summary tolerates incomplete historical records and sorts multiple days`() {
        setup()
        whenever(logs.findCompletedWorkLogsInRange(any(),any(),any())).thenReturn(listOf(log(),log(start.minusDays(1),null),log(start.plusHours(2),30)))
        val result=service.getWorkSummary(worker.id,day.minusDays(1),day)
        assertEquals(90L,result.totalMinutes); assertEquals(1L,result.totalHours)
        assertEquals(3,result.workLogCount); assertEquals(3,result.workLogs.size)
        assertEquals(listOf(day.minusDays(1),day),result.dailySummaries.map { it.date })
        assertEquals(listOf(0L,90L),result.dailySummaries.map { it.totalMinutes })
        assertEquals(listOf(1,2),result.dailySummaries.map { it.workLogCount })
    }
    @Test fun `missing worker is rejected by summary`() {
        assertThrows<ResourceNotFoundException> { service.getWorkSummary(worker.id,day,day) }
        verifyNoInteractions(logs,wages)
    }
    @Test fun `empty payroll has zero totals and no details`() {
        setup();whenever(logs.findConfirmedWorkLogsInRange(any(),any(),any())).thenReturn(emptyList())
        val result=service.calculatePayroll(worker.id,day,day)
        assertEquals(BigDecimal.ZERO,result.totalPay);assertEquals(0.0,result.totalHours);assertTrue(result.wageDetails.isEmpty())
        val monthly=service.getWorkerMonthlyPayrollDetail(worker.id,2100,12)
        assertEquals(0L,monthly.totalWorkMinutes);assertEquals(BigDecimal.ZERO,monthly.totalAmount);assertTrue(monthly.dailyDetails.isEmpty())
        verifyNoInteractions(wages)
    }
    @Test fun `owner payroll rejects foreign worker even when identifier exists`() {
        setup();whenever(owners.findByUserId(owner.user.id)).thenReturn(Owner(user=owner.user))
        assertEquals("Worker does not belong to this owner",assertThrows<BusinessException> {
            service.getMonthlyPayrollSummary(owner.user.id,2024,2,worker.id)
        }.message)
        verifyNoInteractions(logs,wages)
    }
    @Test fun `owner all-worker and filtered payroll use inclusive calendar month`() {
        setup();whenever(owners.findByUserId(owner.user.id)).thenReturn(owner)
        whenever(workers.findByOwner(owner.id)).thenReturn(listOf(worker))
        whenever(logs.findConfirmedWorkLogsInRange(any(),any(),any())).thenReturn(emptyList())
        assertEquals(listOf(worker.id),service.getMonthlyPayrollSummary(owner.user.id,1900,1,null).map { it.workerId })
        assertEquals(listOf(worker.id),service.getMonthlyPayrollSummary(owner.user.id,2100,12,worker.id).map { it.workerId })
        verify(logs).findConfirmedWorkLogsInRange(worker,OffsetDateTime.parse("1900-01-01T00:00:00Z"),OffsetDateTime.parse("1900-02-01T00:00:00Z"))
        verify(logs).findConfirmedWorkLogsInRange(worker,OffsetDateTime.parse("2100-12-01T00:00:00Z"),OffsetDateTime.parse("2101-01-01T00:00:00Z"))
    }

    @Test fun `filtered monthly payroll rejects missing worker after resolving owner`() {
        whenever(owners.findByUserId(owner.user.id)).thenReturn(owner)
        assertThrows<ResourceNotFoundException> { service.getMonthlyPayrollSummary(owner.user.id,2024,2,worker.id) }
        verifyNoInteractions(logs,wages)
    }
}
