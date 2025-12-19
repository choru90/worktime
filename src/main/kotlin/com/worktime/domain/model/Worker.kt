package com.worktime.domain.model

import jakarta.persistence.*
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
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id", foreignKey = ForeignKey(name = "fk_worker_user_account"))
    var userAccount: User? = null,
    
    @Column(nullable = false)
    var active: Boolean = true
) : BaseEntity() {
    
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
