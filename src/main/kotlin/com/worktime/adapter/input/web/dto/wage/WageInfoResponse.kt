package com.worktime.adapter.input.web.dto.wage

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Response DTO for wage information on a specific date.
 */
@Schema(description = "Wage information for a specific date")
data class WageInfoResponse(
    @Schema(description = "The date for which wage was queried", example = "2025-01-15")
    val date: LocalDate,
    
    @Schema(description = "Hourly wage on that date", example = "10000")
    val hourlyWage: BigDecimal?,
    
    @Schema(description = "Whether a wage contract exists for this date")
    val hasContract: Boolean
)
