package com.worktime.domain.model

import jakarta.persistence.*
import java.util.*

/**
 * Store (영업장) 엔티티
 * Owner가 소유하는 영업장을 나타냅니다.
 * 하나의 Owner는 여러 영업장을 가질 수 있습니다.
 */
@Entity
@Table(
    name = "stores",
    indexes = [
        Index(name = "idx_store_owner", columnList = "owner_id"),
        Index(name = "idx_store_active", columnList = "active")
    ]
)
class Store(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    val id: UUID = UUID.randomUUID(),
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false, foreignKey = ForeignKey(name = "fk_store_owner"))
    val owner: Owner,
    
    /**
     * 영업장 이름 (예: "카페 A점", "편의점 본점")
     */
    @Column(nullable = false, length = 100)
    var name: String,
    
    /**
     * 영업장 주소
     */
    @Column(length = 255)
    var address: String? = null,
    
    /**
     * 영업장의 타임존 (예: "Asia/Seoul")
     * 영업시간과 스케줄 표시에 사용됩니다
     */
    @Column(nullable = false, length = 50)
    var timezone: String = "Asia/Seoul",
    
    /**
     * 영업장 활성 상태
     */
    @Column(nullable = false)
    var active: Boolean = true
) : BaseEntity() {
    
    /**
     * 영업장을 비활성화합니다
     */
    fun deactivate() {
        this.active = false
    }
    
    /**
     * 영업장을 활성화합니다
     */
    fun activate() {
        this.active = true
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Store) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "Store(id=$id, name='$name', address=$address, timezone='$timezone', active=$active)"
    }
}
