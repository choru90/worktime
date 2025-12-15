package com.worktime.adapter.input.web

import com.worktime.adapter.input.web.dto.dashboard.DashboardSummaryResponse
import com.worktime.application.port.input.DashboardReadUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

/**
 * 사장용 대시보드 관리 컨트롤러
 */
@RestController
@RequestMapping("/api/owner/dashboard")
@Tag(name = "Dashboard", description = "사장용 대시보드 KPI API")
class DashboardController(
    private val dashboardReadUseCase: DashboardReadUseCase
) {

    @GetMapping("/summary")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "대시보드 KPI 요약 조회", description = "오늘의 KPI(근무/스케줄/알림)와 이번 달 급여 요약을 조회합니다.")
    fun getSummary(
        @Parameter(description = "기준 날짜 (YYYY-MM-DD). 미제공 시 오늘(KST) 기준")
        @RequestParam(required = false) 
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?
    ): ResponseEntity<DashboardSummaryResponse> {
        val ownerId = getCurrentOwnerId()
        // KST 기준 오늘 날짜 (파라미터가 없으면)
        val targetDate = date ?: LocalDate.now(ZoneId.of("Asia/Seoul"))
        
        val summary = dashboardReadUseCase.getDashboardSummary(ownerId, targetDate)
        return ResponseEntity.ok(summary)
    }

    private fun getCurrentOwnerId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
        // name이 ID(UUID)라고 가정하는 컨벤션 따름
        return UUID.fromString(authentication.name)
    }
}
