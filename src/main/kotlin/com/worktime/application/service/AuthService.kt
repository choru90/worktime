package com.worktime.application.service

import com.worktime.adapter.input.web.dto.auth.LoginRequest
import com.worktime.adapter.input.web.dto.auth.LoginResponse
import com.worktime.adapter.input.web.dto.auth.OwnerSignUpRequest
import com.worktime.adapter.input.web.dto.auth.SignUpRequest
import com.worktime.adapter.input.web.dto.auth.WorkerSignUpRequest
import com.worktime.application.port.input.AuthUseCase
import com.worktime.application.port.output.OwnerRepository
import com.worktime.application.port.output.UserRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.auth.security.JwtTokenProvider
import com.worktime.domain.exception.BusinessException
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
    
    override fun signUpWorker(request: WorkerSignUpRequest): LoginResponse {
        // 초대 코드로 Worker 조회
        val worker = workerRepository.findByInviteCode(request.inviteCode.uppercase())
            ?: throw ResourceNotFoundException("유효하지 않은 초대 코드입니다")
        
        // 초대 코드 유효성 검사
        if (!worker.isInviteCodeValid()) {
            throw BusinessException("초대 코드가 만료되었습니다. 사장님에게 새 코드를 요청하세요.")
        }
        
        // 이미 계정이 연결된 경우
        if (worker.userAccount != null) {
            throw BusinessException("이미 계정이 연결된 알바생입니다")
        }
        
        // 이메일 중복 체크
        if (userRepository.existsByEmail(request.email)) {
            throw EmailAlreadyExistsException(request.email)
        }
        
        // User 생성 (role = WORKER)
        val user = User(
            email = request.email,
            password = passwordEncoder.encode(request.password),
            name = worker.name, // Worker의 이름 사용
            role = UserRole.WORKER
        )
        val savedUser = userRepository.save(user)
        
        // Worker와 User 연결
        worker.linkUserAccount(savedUser)
        workerRepository.save(worker)
        
        // JWT 토큰 생성
        val token = jwtTokenProvider.generateToken(savedUser.id, savedUser.role)
        
        return LoginResponse(
            accessToken = token,
            userId = savedUser.id,
            role = savedUser.role,
            workerId = worker.id
        )
    }
    
    override fun login(request: LoginRequest): LoginResponse {
        // 사용자 조회
        val user = userRepository.findByEmail(request.email)
            ?: throw InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다")
        println(user.password)
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
        
        // Worker 정보 조회 (WORKER인 경우)
        val workerId = if (user.role == UserRole.WORKER) {
            workerRepository.findByUserAccountId(user.id)?.id
        } else {
            null
        }
        
        return LoginResponse(
            accessToken = token,
            userId = user.id,
            role = user.role,
            ownerId = ownerId,
            workerId = workerId
        )
    }
}

