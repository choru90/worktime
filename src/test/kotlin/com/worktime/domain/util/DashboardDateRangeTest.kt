package com.worktime.domain.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * DashboardDateRange 단위 테스트
 * 
 * KST 기준 날짜가 UTC OffsetDateTime 범위로 정확히 변환되는지 검증합니다.
 */
@DisplayName("DashboardDateRange 테스트")
class DashboardDateRangeTest {
    
    private val KST = ZoneId.of("Asia/Seoul")
    
    @Test
    @DisplayName("todayRange: KST 날짜가 올바른 UTC 범위로 변환되어야 함")
    fun todayRange_shouldReturnCorrectKstToUtcRange() {
        // Given: 2025년 12월 15일 (KST)
        val kstDate = LocalDate.of(2025, 12, 15)
        
        // When: 오늘 범위 계산
        val range = DashboardDateRange.todayRange(kstDate)
        
        // Then: KST 2025-12-15 00:00:00 ~ 2025-12-16 00:00:00를 UTC로 변환한 값
        // KST는 UTC+9이므로 9시간을 빼야 함
        val expectedStart = kstDate.atStartOfDay(KST).toOffsetDateTime()
        val expectedEnd = kstDate.plusDays(1).atStartOfDay(KST).toOffsetDateTime()
        
        assertEquals(expectedStart, range.start)
        assertEquals(expectedEnd, range.endExclusive)
        
        // 반개구간 검증: start < end
        assertTrue(range.start.isBefore(range.endExclusive))
        
        // 정확히 24시간 차이 검증
        assertEquals(24 * 60 * 60, 
            java.time.Duration.between(range.start, range.endExclusive).seconds)
    }
    
    @Test
    @DisplayName("todayRange: 여름철(DST 미적용) 날짜도 올바르게 변환되어야 함")
    fun todayRange_summerDate_shouldWorkCorrectly() {
        // Given: 2025년 7월 15일 (KST, 한국은 DST 없음)
        val kstDate = LocalDate.of(2025, 7, 15)
        
        // When
        val range = DashboardDateRange.todayRange(kstDate)
        
        // Then
        val expectedStart = kstDate.atStartOfDay(KST).toOffsetDateTime()
        val expectedEnd = kstDate.plusDays(1).atStartOfDay(KST).toOffsetDateTime()
        
        assertEquals(expectedStart, range.start)
        assertEquals(expectedEnd, range.endExclusive)
    }
    
    @Test
    @DisplayName("monthRange: 전체 월 범위가 올바르게 계산되어야 함")
    fun monthRange_shouldReturnWholeMonth() {
        // Given: 2025년 12월 중 임의의 날짜
        val kstDate = LocalDate.of(2025, 12, 15)
        
        // When: 월 범위 계산
        val range = DashboardDateRange.monthRange(kstDate)
        
        // Then: 2025-12-01 00:00:00 ~ 2026-01-01 00:00:00 (KST → UTC)
        val firstDay = LocalDate.of(2025, 12, 1)
        val firstDayNextMonth = LocalDate.of(2026, 1, 1)
        
        val expectedStart = firstDay.atStartOfDay(KST).toOffsetDateTime()
        val expectedEnd = firstDayNextMonth.atStartOfDay(KST).toOffsetDateTime()
        
        assertEquals(expectedStart, range.start)
        assertEquals(expectedEnd, range.endExclusive)
        
        // 12월은 31일이므로 31일 차이 검증
        assertEquals(31, 
            java.time.Duration.between(range.start, range.endExclusive).toDays())
    }
    
    @Test
    @DisplayName("monthRange: 2월(평년) 범위가 올바르게 계산되어야 함")
    fun monthRange_february_nonLeapYear_shouldHandle28Days() {
        // Given: 2025년 2월 (평년 - 28일)
        val kstDate = LocalDate.of(2025, 2, 15)
        
        // When
        val range = DashboardDateRange.monthRange(kstDate)
        
        // Then: 2025-02-01 ~ 2025-03-01
        val expectedStart = LocalDate.of(2025, 2, 1).atStartOfDay(KST).toOffsetDateTime()
        val expectedEnd = LocalDate.of(2025, 3, 1).atStartOfDay(KST).toOffsetDateTime()
        
        assertEquals(expectedStart, range.start)
        assertEquals(expectedEnd, range.endExclusive)
        
        // 28일 차이 검증
        assertEquals(28, 
            java.time.Duration.between(range.start, range.endExclusive).toDays())
    }
    
    @Test
    @DisplayName("monthRange: 2월(윤년) 범위가 올바르게 계산되어야 함")
    fun monthRange_february_leapYear_shouldHandle29Days() {
        // Given: 2024년 2월 (윤년 - 29일)
        val kstDate = LocalDate.of(2024, 2, 15)
        
        // When
        val range = DashboardDateRange.monthRange(kstDate)
        
        // Then: 2024-02-01 ~ 2024-03-01
        val expectedStart = LocalDate.of(2024, 2, 1).atStartOfDay(KST).toOffsetDateTime()
        val expectedEnd = LocalDate.of(2024, 3, 1).atStartOfDay(KST).toOffsetDateTime()
        
        assertEquals(expectedStart, range.start)
        assertEquals(expectedEnd, range.endExclusive)
        
        // 29일 차이 검증
        assertEquals(29, 
            java.time.Duration.between(range.start, range.endExclusive).toDays())
    }
    
    @Test
    @DisplayName("monthRange: 12월 범위는 연도를 넘어가야 함")
    fun monthRange_december_shouldCrossYear() {
        // Given: 2025년 12월
        val kstDate = LocalDate.of(2025, 12, 25)
        
        // When
        val range = DashboardDateRange.monthRange(kstDate)
        
        // Then: 2025-12-01 ~ 2026-01-01
        val expectedStart = LocalDate.of(2025, 12, 1).atStartOfDay(KST).toOffsetDateTime()
        val expectedEnd = LocalDate.of(2026, 1, 1).atStartOfDay(KST).toOffsetDateTime()
        
        assertEquals(expectedStart, range.start)
        assertEquals(expectedEnd, range.endExclusive)
        
        // 연도가 바뀌는지 확인
        assertEquals(2025, range.start.year)
        assertEquals(2026, range.endExclusive.year)
    }
    
    @Test
    @DisplayName("DateTimeRange: 시작 시간이 종료 시간 이후면 예외 발생")
    fun dateTimeRange_invalidRange_shouldThrowException() {
        // Given: 잘못된 범위 (end가 start보다 이전)
        val start = OffsetDateTime.now()
        val end = start.minusHours(1)
        
        // When & Then: 예외 발생
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DateTimeRange(start, end)
        }
        
        assertTrue(exception.message!!.contains("시작 시간은 종료 시간보다 이전이어야 합니다"))
    }
    
    @Test
    @DisplayName("todayRange와 monthRange: 같은 월의 모든 날짜는 같은 월 범위를 가져야 함")
    fun monthRange_allDaysInSameMonth_shouldReturnSameRange() {
        // Given: 2025년 12월의 여러 날짜
        val dates = listOf(
            LocalDate.of(2025, 12, 1),
            LocalDate.of(2025, 12, 15),
            LocalDate.of(2025, 12, 31)
        )
        
        // When: 각 날짜의 월 범위 계산
        val ranges = dates.map { DashboardDateRange.monthRange(it) }
        
        // Then: 모두 같은 범위여야 함
        assertEquals(ranges[0], ranges[1])
        assertEquals(ranges[1], ranges[2])
    }
}
