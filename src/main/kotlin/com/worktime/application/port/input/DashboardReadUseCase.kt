package com.worktime.application.port.input

import java.time.LocalDate
import java.time.YearMonth
import java.util.*

/**
 * 대시보드 조회 전용 UseCase (Input Port)
 * 
 * 대시보드에 표시할 KPI 및 집계 데이터를 조회하는 인터페이스입니다.
 * Read-Only 작업만 수행하며, 도메인 모델의 상태를 변경하지 않습니다.
 */
interface DashboardReadUseCase {
    
    /**
     * 대시보드 요약 정보 조회
     * 
     * @param ownerId 고용주 ID
     * @param date 기준 날짜 (KST)
     * @return 대시보드 요약 응답 (KPI 데이터)
     */
    fun getDashboardSummary(ownerId: UUID, date: LocalDate): com.worktime.adapter.input.web.dto.dashboard.DashboardSummaryResponse
}
