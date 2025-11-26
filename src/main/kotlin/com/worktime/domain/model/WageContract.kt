package com.worktime.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

/**
 * Wage contract (시급 계약) entity.
 * Represents a time-period based wage contract for a worker.
 * If endDate is null, the contract is currently active.
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
     * If null, this contract is currently active
     */
    @Column(nullable = true)
    var endDate: LocalDate? = null
) : BaseEntity() {
    
    /**
     * Check if this contract is active on a given date
     */
    fun isActiveOn(date: LocalDate): Boolean {
        return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate))
    }
    
    /**
     * Check if this contract is currently active (no end date)
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
