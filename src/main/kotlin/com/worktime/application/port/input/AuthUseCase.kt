package com.worktime.application.port.input

import com.worktime.adapter.input.web.dto.auth.LoginRequest
import com.worktime.adapter.input.web.dto.auth.LoginResponse
import com.worktime.adapter.input.web.dto.auth.OwnerSignUpRequest
import com.worktime.adapter.input.web.dto.auth.SignUpRequest
import com.worktime.adapter.input.web.dto.auth.WorkerSignUpRequest

/**
 * 인증 관련 Input Port
 */
interface AuthUseCase {
    fun signUp(request: SignUpRequest): LoginResponse
    fun signUpOwner(request: OwnerSignUpRequest): LoginResponse
    fun signUpWorker(request: WorkerSignUpRequest): LoginResponse
    fun login(request: LoginRequest): LoginResponse
}

