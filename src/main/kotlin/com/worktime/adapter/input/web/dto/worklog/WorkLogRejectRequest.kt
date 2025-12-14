package com.worktime.adapter.input.web.dto.worklog

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 근무 기록 반려 요청 DTO
 */
@Schema(description = "근무 기록 반려 요청")
data class WorkLogRejectRequest(
    @Schema(description = "반려 사유", example = "출근 시간이 맞지 않음")
    val reason: String?
)
