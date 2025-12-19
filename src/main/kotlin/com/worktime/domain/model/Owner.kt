package com.worktime.domain.model

import jakarta.persistence.*
import java.util.*

/**
 * Owner (사장) 엔티티
 * 사장의 비즈니스 정보를 관리합니다.
 * User 엔티티와 1:1 관계입니다.
 */
@Entity
@Table(
    name = "owners",
    indexes = [
        Index(name = "idx_owner_user_id", columnList = "user_id", unique = true)
    ]
)
class Owner(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    val id: UUID = UUID.randomUUID(),
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true, foreignKey = ForeignKey(name = "fk_owner_user"))
    val user: User,
    
    @Column(length = 100)
    var businessName: String? = null
) : BaseEntity() {
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Owner) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "Owner(id=$id, businessName='$businessName')"
    }
}
