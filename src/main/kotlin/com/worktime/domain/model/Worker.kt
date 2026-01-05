package com.worktime.domain.model

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.*

/**
 * Worker (알바생) 엔티티
 * 시스템의 아르바이트 근로자를 나타냅니다.
 */
@Entity
@Table(name = "workers")
class Worker(
    @Id
    @Column
    val id: UUID = UUID.randomUUID(),
    
    @Column(nullable = false, length = 100)
    var name: String,
    
    @Column(nullable = false, length = 20)
    var phoneNumber: String,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false, foreignKey = ForeignKey(name = "fk_worker_owner"))
    var owner: Owner,
    
    /**
     * 소속 영업장 (null이면 Owner 소속 모든 영업장에서 근무 가능)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", foreignKey = ForeignKey(name = "fk_worker_store"))
    var store: Store? = null,
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id", foreignKey = ForeignKey(name = "fk_worker_user_account"))
    var userAccount: User? = null,
    
    @Column(nullable = false)
    var active: Boolean = true,
    
    /**
     * 알바생 계정 연결을 위한 초대 코드 (6자리)
     */
    @Column(length = 10, unique = true)
    var inviteCode: String? = null,
    
    /**
     * 초대 코드 만료 시간
     */
    @Column
    var inviteCodeExpiresAt: OffsetDateTime? = null
) : BaseEntity() {
    
    /**
     * 초대 코드 생성 (6자리 영숫자, 24시간 유효)
     */
    fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // 혼동 가능한 문자 제외 (0,O,I,1)
        val code = (1..6).map { chars.random() }.joinToString("")
        this.inviteCode = code
        this.inviteCodeExpiresAt = OffsetDateTime.now().plusHours(24)
        return code
    }
    
    /**
     * 초대 코드 유효성 검사
     */
    fun isInviteCodeValid(): Boolean {
        val code = inviteCode ?: return false
        val expiresAt = inviteCodeExpiresAt ?: return false
        return code.isNotBlank() && OffsetDateTime.now().isBefore(expiresAt)
    }
    
    /**
     * 초대 코드 사용 후 초기화
     */
    fun clearInviteCode() {
        this.inviteCode = null
        this.inviteCodeExpiresAt = null
    }
    
    /**
     * User 계정 연결
     */
    fun linkUserAccount(user: User) {
        this.userAccount = user
        clearInviteCode()
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Worker) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "Worker(id=$id, name='$name', phoneNumber='$phoneNumber', active=$active)"
    }
}

