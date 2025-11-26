package com.worktime.adapter.output.persistence

import com.worktime.domain.model.WageContract
import com.worktime.domain.model.Worker
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

/**
 * JPA Repository for WageContract entity.
 */
@Repository
interface WageContractJpaRepository : JpaRepository<WageContract, UUID> {
    
    /**
     * Find all wage contracts for a worker
     */
    fun findByWorker(worker: Worker): List<WageContract>
    
    /**
     * Find all wage contracts for a worker, ordered by start date descending
     */
    fun findByWorkerOrderByStartDateDesc(worker: Worker): List<WageContract>
    
    /**
     * Find currently active contract (endDate is null) for a worker
     */
    fun findByWorkerAndEndDateIsNull(worker: Worker): WageContract?
    
    /**
     * Find active contract for a worker on a specific date
     */
    @Query("""
        SELECT wc FROM WageContract wc 
        WHERE wc.worker = :worker 
        AND wc.startDate <= :date 
        AND (wc.endDate IS NULL OR wc.endDate >= :date)
    """)
    fun findActiveContractOnDate(
        @Param("worker") worker: Worker,
        @Param("date") date: LocalDate
    ): WageContract?
    
    /**
     * Find all contracts that overlap with a given date range
     */
    @Query("""
        SELECT wc FROM WageContract wc 
        WHERE wc.worker = :worker 
        AND wc.startDate <= :endDate 
        AND (wc.endDate IS NULL OR wc.endDate >= :startDate)
        ORDER BY wc.startDate DESC
    """)
    fun findContractsOverlappingPeriod(
        @Param("worker") worker: Worker,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<WageContract>
}
