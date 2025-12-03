package com.worktime.auth.security

import com.worktime.domain.model.UserRole
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

/**
 * JWT 토큰 생성 및 검증을 담당하는 컴포넌트
 */
@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}")
    private val secretKey: String,
    
    @Value("\${jwt.expiration}")
    private val expirationMs: Long
) {
    
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secretKey.toByteArray())
    }
    
    /**
     * JWT 토큰 생성
     */
    fun generateToken(userId: UUID, role: UserRole): String {
        val now = Date()
        val expiryDate = Date(now.time + expirationMs)
        
        return Jwts.builder()
            .subject(userId.toString())
            .claim("role", role.name)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }
    
    /**
     * 토큰에서 userId 추출
     */
    fun getUserIdFromToken(token: String): UUID {
        val claims = getClaims(token)
        return UUID.fromString(claims.subject)
    }
    
    /**
     * 토큰에서 role 추출
     */
    fun getRoleFromToken(token: String): UserRole {
        val claims = getClaims(token)
        val roleString = claims["role"] as String
        return UserRole.valueOf(roleString)
    }
    
    /**
     * 토큰 유효성 검증
     */
    fun validateToken(token: String): Boolean {
        return try {
            getClaims(token)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
