package com.worktime.adapter.input.web.dto.payroll

import java.math.BigDecimal
import java.util.*

/**
 * 근로자 월별 급여 상세 응답 DTO
 */
data class WorkerPayrollDetailResponse(
    val workerId: UUID,
    val workerName: String,
    val year: Int,
    val month: Int,
    val totalWorkMinutes: Long,
    val totalWorkHours: Double,
    val totalAmount: BigDecimal,
    val dailyDetails: List<DailyPayrollDetail>
)

/**
 * 일별 급여 상세 DTO
 */
data class DailyPayrollDetail(
    val date: String,
    val workMinutes: Long,
    val workHours: Double,
    val hourlyWage: BigDecimal,
    val amount: BigDecimal
)
