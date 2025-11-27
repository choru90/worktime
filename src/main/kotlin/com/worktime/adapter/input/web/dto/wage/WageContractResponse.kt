package com.worktime.adapter.input.web.dto.wage

import com.worktime.domain.model.WageContract
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * Response DTO for wage contract information.
 */
@Schema(description = "Wage contract information")
data class WageContractResponse(
    @Schema(description = "Wage contract ID")
    val id: UUID,
    
    @Schema(description = "Worker ID")
    val workerId: UUID,
    
    @Schema(description = "Hourly wage amount", example = "10000")
    val hourlyWage: BigDecimal,
    
    @Schema(description = "Contract start date", example = "2025-01-01")
    val startDate: LocalDate,
    
    @Schema(description = "Contract end date (null means currently active)", example = "2025-12-31")
    val endDate: LocalDate?,
    
    @Schema(description = "Created timestamp")
    val createdAt: Instant,
    
    @Schema(description = "Last updated timestamp")
    val updatedAt: Instant
) {
    companion object {
        fun from(contract: WageContract): WageContractResponse {
            return WageContractResponse(
                id = contract.id,
                workerId = contract.worker.id,
                hourlyWage = contract.hourlyWage,
                startDate = contract.startDate,
                endDate = contract.endDate,
                createdAt = contract.createdAt,
                updatedAt = contract.updatedAt
            )
        }
    }
}
