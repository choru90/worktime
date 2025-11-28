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
@Tag(name = "시급 계약", description = "시급 계약 관리 API")
class WageContractController(
    private val wageContractUseCase: WageContractUseCase
) {

    @PostMapping("/wage-contracts")
    @Operation(
        summary = "새로운 시급 계약 생성",
        description = "근로자의 새로운 시급 계약을 생성합니다. 기간이 겹치지 않도록 검증합니다."
    )
    fun createWageContract(
        @Parameter(description = "근로자 ID") @PathVariable workerId: UUID,
        @Valid @RequestBody request: WageContractCreateRequest
    ): ResponseEntity<WageContractResponse> {
        val response = wageContractUseCase.createWageContract(workerId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/wage")
    @Operation(
        summary = "특정 날짜의 시급 조회",
        description = "특정 날짜에 적용되는 시급을 조회합니다"
    )
    fun getWageOnDate(
        @Parameter(description = "근로자 ID") @PathVariable workerId: UUID,
        @Parameter(description = "조회할 날짜 (형식: YYYY-MM-DD)")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<WageInfoResponse> {
        val response = wageContractUseCase.getWageOnDate(workerId, date)
        return ResponseEntity.ok(response)
    }
}
