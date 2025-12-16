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
    /**
     * 대시보드 조치 필요 사항 조회 (Action Center)
     *
     * @param ownerId 고용주 ID
     * @param date 기준 날짜 (KST) - 없으면 오늘
     * @param limit 최대 개수
     * @param types 필터링할 타입 목록 (없으면 전체)
     * @return 조치 필요 사항 목록
     */
    fun getDashboardActions(
        ownerId: UUID,
        date: LocalDate? = null,
        limit: Int = 20,
        types: List<com.worktime.domain.model.DashboardActionType>? = null
    ): com.worktime.application.port.input.dto.DashboardActionListResponse

    /**
     * 대시보드 KPI 요약 조회
     * 
     * @param ownerId 고용주 ID
     * @param date 기준 날짜 (KST)
     * @return 대시보드 요약 응답 (KPI 데이터)
     */
    fun getDashboardSummary(ownerId: UUID, date: LocalDate): com.worktime.adapter.input.web.dto.dashboard.DashboardSummaryResponse

    /**
     * 출퇴근 현황 및 예외 조회 (Attendance API)
     * 
     * @param ownerId 고용주 ID
     * @param date 기준 날짜 (KST)
     * @param includeLogWithoutSchedule 스케줄 없는 로그 포함 여부 (Default: false)
     * @return 출퇴근 요약 및 예외 목룍
     */
    fun getAttendanceSummary(
        ownerId: UUID, 
        date: LocalDate, 
        includeLogWithoutSchedule: Boolean = false
    ): com.worktime.adapter.input.web.dto.dashboard.AttendanceSummaryResponse

    /**
     * Top Workers 조회
     */
    fun getTopWorkers(
        ownerId: UUID,
        from: LocalDate?,
        to: LocalDate?,
        limit: Int = 5,
        sort: com.worktime.application.port.input.dto.TopWorkerSort = com.worktime.application.port.input.dto.TopWorkerSort.WAGE_DESC
    ): com.worktime.application.port.input.dto.TopWorkersResponse
}
