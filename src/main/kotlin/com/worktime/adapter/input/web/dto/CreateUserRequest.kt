package com.worktime.adapter.input.web.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * DTO for creating a new user.
 */
@Schema(description = "Request to create a new user")
data class CreateUserRequest(
    @Schema(description = "User's name", example = "홍길동", required = true)
    val name: String,
    
    @Schema(description = "User's email address", example = "hong@example.com", required = true)
    val email: String
)
