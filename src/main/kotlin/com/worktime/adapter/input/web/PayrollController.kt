package com.worktime.adapter.input.web

import com.worktime.adapter.input.web.dto.payroll.PayrollResponse
import com.worktime.adapter.input.web.dto.summary.WorkSummaryResponse
import com.worktime.application.port.input.PayrollUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.*

/**
 * REST Controller for Payroll and Work Summary
 */
@RestController
@RequestMapping("/api/workers/{workerId}")
@Tag(name = "Payroll", description = "급여 및 근무 요약 API")
class PayrollController(
    private val payrollUseCase: PayrollUseCase
) {
    
    @GetMapping("/work-summary")
    @Operation(summary = "근무 요약 조회", description = "근로자의 기간별 근무 시간 요약을 조회합니다")
    @PreAuthorize("@worktimeAccess.worker(#workerId)")
    fun getWorkSummary(
        @PathVariable workerId: UUID,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate
    ): ResponseEntity<WorkSummaryResponse> {
        val response = payrollUseCase.getWorkSummary(workerId, from, to)
        return ResponseEntity.ok(response)
    }
    
    @GetMapping("/payroll")
    @Operation(summary = "급여 계산", description = "근로자의 기간별 급여를 계산합니다")
    @PreAuthorize("@worktimeAccess.worker(#workerId)")
    fun calculatePayroll(

        @PathVariable workerId: UUID,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate
    ): ResponseEntity<PayrollResponse> {
        val response = payrollUseCase.calculatePayroll(workerId, from, to)
        return ResponseEntity.ok(response)
    }
}
