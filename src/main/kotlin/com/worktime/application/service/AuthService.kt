package com.worktime.application.service

import com.worktime.adapter.input.web.dto.auth.LoginRequest
import com.worktime.adapter.input.web.dto.auth.LoginResponse
import com.worktime.adapter.input.web.dto.auth.OwnerSignUpRequest
import com.worktime.adapter.input.web.dto.auth.SignUpRequest
import com.worktime.application.port.input.AuthUseCase
import com.worktime.application.port.output.OwnerRepository
import com.worktime.application.port.output.UserRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.auth.security.JwtTokenProvider
import com.worktime.domain.exception.EmailAlreadyExistsException
import com.worktime.domain.exception.InvalidCredentialsException
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.model.Owner
import com.worktime.domain.model.User
import com.worktime.domain.model.UserRole
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 인증 관련 비즈니스 로직 구현
 */
@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val ownerRepository: OwnerRepository,
    private val workerRepository: WorkerRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider
) : AuthUseCase {
    
    override fun signUp(request: SignUpRequest): LoginResponse {
        // 이메일 중복 체크
        if (userRepository.existsByEmail(request.email)) {
            throw EmailAlreadyExistsException(request.email)
        }
        
        // Worker 연결 (WORKER 역할인 경우)
        val worker = if (request.workerId != null) {
            workerRepository.findById(request.workerId)
                ?: throw ResourceNotFoundException("Worker not found: ${request.workerId}")
        } else {
            null
        }
        
        // User 생성
        val user = User(
            email = request.email,
            password = passwordEncoder.encode(request.password),
            name = request.name,
            role = request.role
        )
        
        val savedUser = userRepository.save(user)
        
        // JWT 토큰 생성
        val token = jwtTokenProvider.generateToken(savedUser.id, savedUser.role)
        
        return LoginResponse(
            accessToken = token,
            userId = savedUser.id,
            role = savedUser.role,
            workerId = worker?.id
        )
    }
    
    override fun signUpOwner(request: OwnerSignUpRequest): LoginResponse {
        // 이메일 중복 체크
        if (userRepository.existsByEmail(request.email)) {
            throw EmailAlreadyExistsException(request.email)
        }
        
        // User 생성 (role = OWNER)
        val user = User(
            email = request.email,
            password = passwordEncoder.encode(request.password),
            name = request.name,
            role = UserRole.OWNER
        )
        val savedUser = userRepository.save(user)
        
        // Owner 생성
        val owner = Owner(
            user = savedUser,
            businessName = request.businessName
        )
        val savedOwner = ownerRepository.save(owner)
        
        // JWT 토큰 생성
        val token = jwtTokenProvider.generateToken(savedUser.id, savedUser.role)
        
        return LoginResponse(
            accessToken = token,
            userId = savedUser.id,
            role = savedUser.role,
            ownerId = savedOwner.id
        )
    }
    
    override fun login(request: LoginRequest): LoginResponse {
        // 사용자 조회
        val user = userRepository.findByEmail(request.email)
            ?: throw InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다")
        
        // 비밀번호 검증
        if (!passwordEncoder.matches(request.password, user.password)) {
            throw InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다")
        }
        
        // JWT 토큰 생성
        val token = jwtTokenProvider.generateToken(user.id, user.role)
        
        // Owner 정보 조회 (OWNER인 경우)
        val ownerId = if (user.role == UserRole.OWNER) {
            ownerRepository.findByUserId(user.id)?.id
        } else {
            null
        }
        
        return LoginResponse(
            accessToken = token,
            userId = user.id,
            role = user.role,
            ownerId = ownerId
        )
    }
}

