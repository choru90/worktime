package com.worktime.adapter.input.web.dto.schedule

/**
 * 스케줄 목록 응답 DTO
 */
data class ScheduleListResponse(
    val schedules: List<ScheduleResponse>,
    val totalCount: Int
) {
    companion object {
        fun from(schedules: List<ScheduleResponse>): ScheduleListResponse {
            return ScheduleListResponse(
                schedules = schedules,
                totalCount = schedules.size
            )
        }
    }
}
