package com.worktime.adapter.input.web.dto.wage

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Request DTO for creating a new wage contract.
 */
@Schema(description = "Request to create a new wage contract")
data class WageContractCreateRequest(
    @field:NotNull(message = "Hourly wage is required")
    @field:DecimalMin(value = "0.0", inclusive = false, message = "Hourly wage must be greater than 0")
    @Schema(description = "Hourly wage amount", example = "10000", required = true)
    val hourlyWage: BigDecimal,
    
    @field:NotNull(message = "Start date is required")
    @Schema(description = "Contract start date", example = "2025-01-01", required = true)
    val startDate: LocalDate,
    
    @Schema(description = "Contract end date (null means currently active)", example = "2025-12-31", required = false)
    val endDate: LocalDate? = null
)
