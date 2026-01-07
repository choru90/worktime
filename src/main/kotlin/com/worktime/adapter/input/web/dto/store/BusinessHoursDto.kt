package com.worktime.adapter.input.web.dto.store

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * 영업시간 설정 요청 (단일 요일)
 */
data class BusinessHoursRequest(
    val dayOfWeek: DayOfWeek,
    val openTime: LocalTime,
    val closeTime: LocalTime,
    val isClosed: Boolean = false
)

/**
 * 영업시간 전체 설정 요청 (7일)
 */
data class BusinessHoursListRequest(
    val hours: List<BusinessHoursRequest>
)

/**
 * 영업시간 응답
 */
data class BusinessHoursResponse(
    val dayOfWeek: DayOfWeek,
    val openTime: LocalTime,
    val closeTime: LocalTime,
    val isClosed: Boolean
) {
    companion object {
        fun from(businessHours: com.worktime.domain.model.BusinessHours): BusinessHoursResponse {
            return BusinessHoursResponse(
                dayOfWeek = businessHours.dayOfWeek,
                openTime = businessHours.openTime,
                closeTime = businessHours.closeTime,
                isClosed = businessHours.isClosed
            )
        }
    }
}
