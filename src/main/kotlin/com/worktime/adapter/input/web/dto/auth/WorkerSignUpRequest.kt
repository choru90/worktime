package com.worktime.adapter.input.web.dto.auth

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 알바생 회원가입 요청 DTO
 */
@Schema(description = "알바생 회원가입 요청")
data class WorkerSignUpRequest(
    @field:NotBlank(message = "초대 코드를 입력해주세요")
    @field:Size(min = 6, max = 10, message = "초대 코드는 6자리입니다")
    @Schema(description = "초대 코드", example = "ABC123")
    val inviteCode: String,
    
    @field:NotBlank(message = "이메일을 입력해주세요")
    @field:Email(message = "올바른 이메일 형식이 아닙니다")
    @Schema(description = "이메일", example = "worker@example.com")
    val email: String,
    
    @field:NotBlank(message = "비밀번호를 입력해주세요")
    @field:Size(min = 6, message = "비밀번호는 6자 이상이어야 합니다")
    @Schema(description = "비밀번호", example = "password123")
    val password: String
)
