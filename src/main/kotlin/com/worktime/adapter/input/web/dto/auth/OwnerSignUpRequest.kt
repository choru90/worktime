package com.worktime.adapter.input.web.dto.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Owner 회원가입 요청 DTO
 */
data class OwnerSignUpRequest(
    @field:NotBlank(message = "이메일은 필수입니다")
    @field:Email(message = "이메일 형식이 올바르지 않습니다")
    val email: String,
    
    @field:NotBlank(message = "비밀번호는 필수입니다")
    @field:Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다")
    val password: String,
    
    @field:NotBlank(message = "이름은 필수입니다")
    val name: String,
    
    val businessName: String? = null
)
