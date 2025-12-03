package com.worktime.adapter.input.web.dto.auth

import com.worktime.domain.model.UserRole
import java.util.*

/**
 * 로그인 응답 DTO
 */
data class LoginResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val userId: UUID,
    val role: UserRole,
    val workerId: UUID? = null
)
