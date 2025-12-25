package com.worktime.application.port.input

import com.worktime.adapter.input.web.dto.payroll.PayrollResponse
import com.worktime.adapter.input.web.dto.summary.WorkSummaryResponse
import java.time.LocalDate
import java.util.*

/**
 * Payroll UseCase interface (Input Port)
 */
interface PayrollUseCase {
    
    /**
     * 근무 요약 조회
     */
    fun getWorkSummary(
        workerId: UUID,
        from: LocalDate,
        to: LocalDate
    ): WorkSummaryResponse
    
    /**
     * 급여 계산
     */
    fun calculatePayroll(
        workerId: UUID,
        from: LocalDate,
        to: LocalDate
    ): PayrollResponse
    
    /**
     * 월별 급여 요약 조회 (OWNER 전용)
     */
    fun getMonthlyPayrollSummary(
        userId: UUID,
        year: Int,
        month: Int,
        workerId: UUID?
    ): List<PayrollResponse>
    
    /**
     * 특정 근로자 월별 급여 상세 조회
     */
    fun getWorkerMonthlyPayrollDetail(
        workerId: UUID,
        year: Int,
        month: Int
    ): com.worktime.adapter.input.web.dto.payroll.WorkerPayrollDetailResponse
}
