package com.worktime.adapter.input.web

import com.worktime.adapter.input.web.dto.wage.WageContractCreateRequest
import com.worktime.adapter.input.web.dto.wage.WageContractResponse
import com.worktime.adapter.input.web.dto.wage.WageInfoResponse
import com.worktime.application.port.input.WageContractUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.*

/**
 * REST Controller for WageContract operations.
 */
@RestController
@RequestMapping("/api/workers/{workerId}")
@Tag(name = "Wage Contract", description = "Wage contract management API")
class WageContractController(
    private val wageContractUseCase: WageContractUseCase
) {

    @PostMapping("/wage-contracts")
    @Operation(
        summary = "Create a new wage contract",
        description = "Creates a new wage contract for a worker. Validates that periods do not overlap."
    )
    fun createWageContract(
        @Parameter(description = "Worker ID") @PathVariable workerId: UUID,
        @Valid @RequestBody request: WageContractCreateRequest
    ): ResponseEntity<WageContractResponse> {
        val response = wageContractUseCase.createWageContract(workerId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/wage")
    @Operation(
        summary = "Get wage on a specific date",
        description = "Retrieves the hourly wage applicable on a specific date"
    )
    fun getWageOnDate(
        @Parameter(description = "Worker ID") @PathVariable workerId: UUID,
        @Parameter(description = "Date to query (format: YYYY-MM-DD)")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<WageInfoResponse> {
        val response = wageContractUseCase.getWageOnDate(workerId, date)
        return ResponseEntity.ok(response)
    }
}
