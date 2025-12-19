package com.worktime.domain.model

import jakarta.persistence.*
import java.util.*

/**
 * User (사용자 계정) 엔티티
 * 시스템의 로그인 계정 정보를 나타냅니다.
 * OWNER(사장) 또는 WORKER(알바생) 역할을 가집니다.
 */
@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_user_email", columnList = "email", unique = true)
    ]
)
class User(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    val id: UUID = UUID.randomUUID(),
    
    @Column(nullable = false, unique = true, length = 255)
    var email: String,
    
    @Column(nullable = false, length = 255)
    var password: String,
    
    @Column(nullable = false, length = 100)
    var name: String,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: UserRole
) : BaseEntity() {
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "User(id=$id, email='$email', name='$name', role=$role)"
    }
}
