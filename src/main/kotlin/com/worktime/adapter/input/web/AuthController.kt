package com.worktime.adapter.input.web

import com.worktime.adapter.input.web.dto.auth.LoginRequest
import com.worktime.adapter.input.web.dto.auth.LoginResponse
import com.worktime.adapter.input.web.dto.auth.SignUpRequest
import com.worktime.application.port.input.AuthUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 인증 관련 REST Controller
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "인증", description = "회원가입 및 로그인 API")
class AuthController(
    private val authUseCase: AuthUseCase
) {
    
    @PostMapping("/signup")
    @Operation(
        summary = "회원가입",
        description = "새로운 사용자 계정을 생성합니다"
    )
    fun signUp(
        @Valid @RequestBody request: SignUpRequest
    ): ResponseEntity<LoginResponse> {
        val response = authUseCase.signUp(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
    
    @PostMapping("/login")
    @Operation(
        summary = "로그인",
        description = "이메일과 비밀번호로 로그인하여 JWT 토큰을 발급받습니다"
    )
    fun login(
        @Valid @RequestBody request: LoginRequest
    ): ResponseEntity<LoginResponse> {
        val response = authUseCase.login(request)
        return ResponseEntity.ok(response)
    }
}
