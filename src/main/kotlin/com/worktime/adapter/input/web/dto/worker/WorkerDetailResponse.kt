package com.worktime.adapter.input.web.dto.worker

import com.worktime.adapter.input.web.dto.wage.WageContractResponse
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Response DTO for worker detailed information including wage contract info.
 */
@Schema(description = "Worker detailed information with wage contracts")
data class WorkerDetailResponse(
    @Schema(description = "Basic worker information")
    val worker: WorkerResponse,
    
    @Schema(description = "Current active wage contract")
    val currentWage: WageContractResponse?,
    
    @Schema(description = "Historical wage contracts")
    val wageHistory: List<WageContractResponse>
)
