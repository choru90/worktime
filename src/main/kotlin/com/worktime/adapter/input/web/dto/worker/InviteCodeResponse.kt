package com.worktime.adapter.input.web.dto.worker

import java.time.OffsetDateTime

/**
 * 초대 코드 응답 DTO
 */
data class InviteCodeResponse(
    val inviteCode: String,
    val expiresAt: OffsetDateTime,
    val workerName: String
)
