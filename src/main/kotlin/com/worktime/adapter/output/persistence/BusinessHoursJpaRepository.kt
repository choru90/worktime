package com.worktime.adapter.output.persistence

import com.worktime.domain.model.BusinessHours
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.DayOfWeek
import java.util.*

/**
 * BusinessHours JPA Repository
 */
@Repository
interface BusinessHoursJpaRepository : JpaRepository<BusinessHours, UUID> {
    
    /**
     * 영업장의 모든 영업시간 조회
     */
    fun findByStoreId(storeId: UUID): List<BusinessHours>
    
    /**
     * 영업장의 특정 요일 영업시간 조회
     */
    fun findByStoreIdAndDayOfWeek(storeId: UUID, dayOfWeek: DayOfWeek): BusinessHours?
    
    /**
     * 영업장의 영업일(휴무일 제외) 조회
     */
    fun findByStoreIdAndIsClosedFalse(storeId: UUID): List<BusinessHours>
    
    /**
     * 영업장의 영업시간 삭제
     */
    fun deleteByStoreId(storeId: UUID)
}
