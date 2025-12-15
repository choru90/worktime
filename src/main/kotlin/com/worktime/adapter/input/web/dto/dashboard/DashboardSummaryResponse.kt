package com.worktime.adapter.input.web.dto.dashboard

import java.math.BigDecimal

/**
 * 대시보드 요약 응답 DTO
 * 
 * 사장용 대시보드 상단 KPI 카드에 표시할 데이터를 제공합니다.
 */
data class DashboardSummaryResponse(
    /**
     * 기준 날짜 (YYYY-MM-DD format)
     */
    val date: String,
    
    /**
     * 타임존 (항상 "Asia/Seoul")
     */
    val timezone: String,
    
    /**
     * 오늘의 KPI 데이터
     */
    val today: TodayKpiDto,
    
    /**
     * 이번 달 KPI 데이터
     */
    val month: MonthKpiDto,
    
    /**
     * 조치 필요 건수 (승인 대기 + 미출근 + 미퇴근)
     */
    val actionNeededCount: Int
)

/**
 * 오늘의 KPI 데이터
 */
data class TodayKpiDto(
    /**
     * 오늘 스케줄된 근무 개수
     */
    val scheduledCount: Int,
    
    /**
     * 출근 체크한 직원 수
     */
    val checkedInCount: Int,
    
    /**
     * 출근 미체크 직원 수 (스케줄은 있는데 출근 안 한 경우)
     */
    val notCheckedInCount: Int,
    
    /**
     * 퇴근 미체크 직원 수 (출근했는데 퇴근 안 한 경우)
     */
    val notCheckedOutCount: Int,
    
    /**
     * 오늘 총 근무 시간 (분 단위)
     */
    val totalWorkMinutes: Long
)

/**
 * 이번 달 KPI 데이터
 */
data class MonthKpiDto(
    /**
     * 이번 달 발생한 실제 급여 (완료된 근무 기록 기반)
     */
    val accruedWage: BigDecimal,
    
    /**
     * 이번 달 예상 급여 (확정된 스케줄 기반)
     * 
     * TODO: MVP에서는 0 반환. Sprint 2+에서 스케줄 기반 예측 구현 예정
     */
    val forecastWage: BigDecimal
)
