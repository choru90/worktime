package com.worktime.adapter.input.web.dto.payroll

import com.worktime.adapter.input.web.dto.summary.PeriodInfo
import java.math.BigDecimal
import java.util.*

/**
 * 급여 계산 응답 DTO
 */
data class PayrollResponse(
    val workerId: UUID,
    val workerName: String,
    val period: PeriodInfo,
    val totalPay: BigDecimal,
    val totalHours: Double,
    val wageDetails: List<WageDetail>
)
