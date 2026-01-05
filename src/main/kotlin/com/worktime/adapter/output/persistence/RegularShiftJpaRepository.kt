package com.worktime.adapter.output.persistence

import com.worktime.domain.model.RegularShift
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.*

/**
 * RegularShift JPA Repository
 */
@Repository
interface RegularShiftJpaRepository : JpaRepository<RegularShift, UUID> {
    
    /**
     * 영업장의 모든 고정근무 조회
     */
    fun findByStoreId(storeId: UUID): List<RegularShift>
    
    /**
     * 영업장의 활성 고정근무 조회
     */
    fun findByStoreIdAndActiveTrue(storeId: UUID): List<RegularShift>
    
    /**
     * 영업장의 특정 요일 고정근무 조회
     */
    fun findByStoreIdAndDayOfWeekAndActiveTrue(storeId: UUID, dayOfWeek: DayOfWeek): List<RegularShift>
    
    /**
     * 특정 Worker의 고정근무 조회
     */
    fun findByWorkerId(workerId: UUID): List<RegularShift>
    
    /**
     * 특정 Worker의 활성 고정근무 조회
     */
    fun findByWorkerIdAndActiveTrue(workerId: UUID): List<RegularShift>
    
    /**
     * 특정 날짜에 유효한 고정근무 조회
     * (해당 요일이고, effectiveFrom <= date <= effectiveUntil 또는 null)
     */
    @Query("""
        SELECT rs FROM RegularShift rs 
        WHERE rs.store.id = :storeId 
        AND rs.dayOfWeek = :dayOfWeek 
        AND rs.active = true
        AND (rs.effectiveFrom IS NULL OR rs.effectiveFrom <= :date)
        AND (rs.effectiveUntil IS NULL OR rs.effectiveUntil >= :date)
    """)
    fun findEffectiveShifts(
        @Param("storeId") storeId: UUID,
        @Param("dayOfWeek") dayOfWeek: DayOfWeek,
        @Param("date") date: LocalDate
    ): List<RegularShift>
}
