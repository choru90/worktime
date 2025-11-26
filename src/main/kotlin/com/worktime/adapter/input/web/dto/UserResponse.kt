package com.worktime.adapter.input.web.dto

import com.worktime.domain.model.User
import io.swagger.v3.oas.annotations.media.Schema

/**
 * DTO for user response.
 */
@Schema(description = "User response")
data class UserResponse(
    @Schema(description = "User ID", example = "1")
    val id: Long,
    
    @Schema(description = "User's name", example = "홍길동")
    val name: String,
    
    @Schema(description = "User's email address", example = "hong@example.com")
    val email: String
) {
    companion object {
        fun from(user: User): UserResponse {
            return UserResponse(
                id = user.id ?: throw IllegalStateException("User ID cannot be null"),
                name = user.name,
                email = user.email
            )
        }
    }
}
