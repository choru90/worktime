package com.worktime.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

/**
 * Wage contract (시급 계약) 엔티티
 * 근로자의 기간별 시급 계약을 나타냅니다.
 * endDate가 null이면 현재 활성화된 계약입니다.
 */
@Entity
@Table(
    name = "wage_contracts",
    indexes = [
        Index(name = "idx_wage_contract_worker", columnList = "worker_id"),
        Index(name = "idx_wage_contract_dates", columnList = "start_date,end_date")
    ]
)
class WageContract(
    @Id
    @Column(columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false, foreignKey = ForeignKey(name = "fk_wage_contract_worker"))
    val worker: Worker,
    
    @Column(nullable = false, precision = 10, scale = 2)
    var hourlyWage: BigDecimal,
    
    @Column(nullable = false)
    var startDate: LocalDate,
    
    /**
     * null인 경우 현재 활성화된 계약입니다
     */
    @Column(nullable = true)
    var endDate: LocalDate? = null
) : BaseEntity() {
    
    /**
     * 특정 날짜에 계약이 활성화되어 있는지 확인합니다
     */
    fun isActiveOn(date: LocalDate): Boolean {
        return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate))
    }
    
    /**
     * 현재 계약이 활성화되어 있는지 확인합니다 (종료일 없음)
     */
    fun isCurrentlyActive(): Boolean {
        return endDate == null
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WageContract) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "WageContract(id=$id, hourlyWage=$hourlyWage, startDate=$startDate, endDate=$endDate)"
    }
}
