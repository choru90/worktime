package com.worktime.domain.model

import jakarta.persistence.*
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.*

/**
 * BusinessHours (영업시간) 엔티티
 * 영업장의 요일별 영업시간을 나타냅니다.
 * 주중/주말 다른 영업시간을 설정할 수 있습니다.
 */
@Entity
@Table(
    name = "business_hours",
    indexes = [
        Index(name = "idx_business_hours_store", columnList = "store_id"),
        Index(name = "idx_business_hours_day", columnList = "day_of_week")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_business_hours_store_day",
            columnNames = ["store_id", "day_of_week"]
        )
    ]
)
class BusinessHours(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    val id: UUID = UUID.randomUUID(),
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = ForeignKey(name = "fk_business_hours_store"))
    val store: Store,
    
    /**
     * 요일 (MONDAY ~ SUNDAY)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    val dayOfWeek: DayOfWeek,
    
    /**
     * 영업 시작 시간 (예: 09:00)
     */
    @Column(name = "open_time", nullable = false)
    var openTime: LocalTime,
    
    /**
     * 영업 종료 시간 (예: 20:00)
     */
    @Column(name = "close_time", nullable = false)
    var closeTime: LocalTime,
    
    /**
     * 휴무일 여부
     * true인 경우 해당 요일은 영업하지 않음
     */
    @Column(name = "is_closed", nullable = false)
    var isClosed: Boolean = false
) : BaseEntity() {
    
    /**
     * 영업시간을 업데이트합니다
     */
    fun updateHours(openTime: LocalTime, closeTime: LocalTime) {
        require(openTime.isBefore(closeTime)) {
            "영업 시작 시간은 종료 시간보다 이전이어야 합니다"
        }
        this.openTime = openTime
        this.closeTime = closeTime
        this.isClosed = false
    }
    
    /**
     * 휴무일로 설정합니다
     */
    fun markAsClosed() {
        this.isClosed = true
    }
    
    /**
     * 영업일로 설정합니다
     */
    fun markAsOpen() {
        this.isClosed = false
    }
    
    /**
     * 특정 시간이 영업시간 내인지 확인합니다
     */
    fun isWithinBusinessHours(time: LocalTime): Boolean {
        if (isClosed) return false
        return !time.isBefore(openTime) && !time.isAfter(closeTime)
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BusinessHours) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "BusinessHours(id=$id, dayOfWeek=$dayOfWeek, openTime=$openTime, closeTime=$closeTime, isClosed=$isClosed)"
    }
}
