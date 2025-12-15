package com.worktime.domain.util

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 날짜/시간 범위를 나타내는 데이터 클래스
 * 
 * @property start 시작 시간 (inclusive)
 * @property endExclusive 종료 시간 (exclusive) - 반개구간 [start, end)
 */
data class DateTimeRange(
    val start: OffsetDateTime,
    val endExclusive: OffsetDateTime
) {
    init {
        require(start.isBefore(endExclusive)) {
            "시작 시간은 종료 시간보다 이전이어야 합니다 (start: $start, end: $endExclusive)"
        }
    }
}

/**
 * 대시보드 날짜 범위 계산 유틸리티
 * 
 * KST(Asia/Seoul) 기준 LocalDate를 입력받아 UTC OffsetDateTime 범위로 변환합니다.
 * 모든 범위는 반개구간 [start, end) 형식을 사용하여 자정 경계 버그를 방지합니다.
 */
object DashboardDateRange {
    
    /**
     * Asia/Seoul 타임존
     */
    private val KST = ZoneId.of("Asia/Seoul")
    
    /**
     * 주어진 KST 날짜의 하루 전체 범위를 UTC로 반환합니다.
     * 
     * 예: 2025-12-15 (KST) 
     * → [2025-12-15 00:00:00 KST, 2025-12-16 00:00:00 KST)
     * → UTC로 변환한 범위
     * 
     * @param kstDate KST 기준 날짜
     * @return UTC 기준 날짜/시간 범위 (반개구간)
     */
    fun todayRange(kstDate: LocalDate): DateTimeRange {
        // KST 자정 (00:00:00)
        val startKst = kstDate.atStartOfDay(KST)
        
        // 다음날 KST 자정 (00:00:00)
        val endKst = kstDate.plusDays(1).atStartOfDay(KST)
        
        // UTC로 변환
        val startUtc = startKst.toOffsetDateTime()
        val endUtc = endKst.toOffsetDateTime()
        
        return DateTimeRange(startUtc, endUtc)
    }
    
    /**
     * 주어진 KST 날짜가 속한 월의 전체 범위를 UTC로 반환합니다.
     * 
     * 예: 2025-12-15 (KST)
     * → [2025-12-01 00:00:00 KST, 2026-01-01 00:00:00 KST)
     * → UTC로 변환한 범위
     * 
     * @param kstDate KST 기준 날짜 (월 중 아무 날짜나 가능)
     * @return UTC 기준 날짜/시간 범위 (반개구간)
     */
    fun monthRange(kstDate: LocalDate): DateTimeRange {
        // 해당 월의 첫날 KST 자정
        val firstDayOfMonth = kstDate.withDayOfMonth(1)
        val startKst = firstDayOfMonth.atStartOfDay(KST)
        
        // 다음 월의 첫날 KST 자정
        val firstDayOfNextMonth = firstDayOfMonth.plusMonths(1)
        val endKst = firstDayOfNextMonth.atStartOfDay(KST)
        
        // UTC로 변환
        val startUtc = startKst.toOffsetDateTime()
        val endUtc = endKst.toOffsetDateTime()
        
        return DateTimeRange(startUtc, endUtc)
    }
    
    // TODO: weekRange 구현
    // 주의: 주의 시작일 정의가 필요합니다 (월요일 시작? 일요일 시작?)
    // Sprint 1에서 요구사항 확인 후 구현 예정
}

/**
 * ZonedDateTime을 OffsetDateTime으로 변환하는 확장 함수
 */
private fun java.time.ZonedDateTime.toOffsetDateTime(): OffsetDateTime {
    return this.toOffsetDateTime()
}
