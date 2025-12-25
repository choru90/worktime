package com.worktime.adapter.input.web

import com.worktime.adapter.input.web.dto.payroll.PayrollResponse
import com.worktime.adapter.input.web.dto.payroll.WorkerPayrollDetailResponse
import com.worktime.application.port.input.PayrollUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * REST Controller for Owner Payroll Operations
 */
@RestController
@RequestMapping("/api/payroll")
@Tag(name = "Owner Payroll", description = "사장용 급여 관리 API")
class OwnerPayrollController(
    private val payrollUseCase: PayrollUseCase
) {
    
    @GetMapping("/owner/monthly")
    @Operation(summary = "월별 급여 요약 조회", description = "모든 근로자 또는 특정 근로자의 월별 급여를 조회합니다 (OWNER 전용)")
    @PreAuthorize("hasRole('OWNER')")
    fun getMonthlyPayrollSummary(
        @RequestParam year: Int,
        @RequestParam month: Int,
        @RequestParam(required = false) workerId: UUID?
    ): ResponseEntity<List<PayrollResponse>> {
        val authentication = SecurityContextHolder.getContext().authentication
        val userId = UUID.fromString(authentication.name)
        val response = payrollUseCase.getMonthlyPayrollSummary(userId, year, month, workerId)
        return ResponseEntity.ok(response)
    }
    
    @GetMapping("/workers/{workerId}/monthly")
    @Operation(summary = "특정 근로자 월별 급여 상세 조회", description = "특정 근로자의 월별 급여 상세를 조회합니다 (OWNER 전용)")
    @PreAuthorize("hasRole('OWNER')")
    fun getWorkerMonthlyPayrollDetail(
        @PathVariable workerId: UUID,
        @RequestParam year: Int,
        @RequestParam month: Int
    ): ResponseEntity<WorkerPayrollDetailResponse> {
        val response = payrollUseCase.getWorkerMonthlyPayrollDetail(workerId, year, month)
        return ResponseEntity.ok(response)
    }
}

