package com.worktime.adapter.input.web.dto.payroll

import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

/**
 * 근무 시급 세부 정보
 */
data class WageDetail(
    val workLogId: UUID,
    val date: LocalDate,
    val hours: Double,
    val hourlyWage: BigDecimal?,
    val pay: BigDecimal
)
