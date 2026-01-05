package com.worktime.application.service

import com.worktime.adapter.output.persistence.BusinessHoursJpaRepository
import com.worktime.adapter.output.persistence.StoreJpaRepository
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.model.BusinessHours
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.*

/**
 * 영업시간 설정 DTO
 */
data class BusinessHoursRequest(
    val dayOfWeek: DayOfWeek,
    val openTime: LocalTime,
    val closeTime: LocalTime,
    val isClosed: Boolean = false
)

/**
 * BusinessHours (영업시간) 관리 서비스
 */
@Service
@Transactional(readOnly = true)
class BusinessHoursService(
    private val businessHoursRepository: BusinessHoursJpaRepository,
    private val storeRepository: StoreJpaRepository
) {
    
    /**
     * 영업장의 영업시간 전체 설정 (7일 한번에)
     */
    @Transactional
    fun setBusinessHours(storeId: UUID, hours: List<BusinessHoursRequest>): List<BusinessHours> {
        val store = storeRepository.findById(storeId)
            .orElseThrow { ResourceNotFoundException("Store not found with id: $storeId") }
        
        // 기존 영업시간 삭제
        businessHoursRepository.deleteByStoreId(storeId)
        
        // 새 영업시간 저장
        val businessHoursList = hours.map { request ->
            require(!request.isClosed || (request.openTime.isBefore(request.closeTime) || request.isClosed)) {
                "영업 시작 시간은 종료 시간보다 이전이어야 합니다"
            }
            
            BusinessHours(
                store = store,
                dayOfWeek = request.dayOfWeek,
                openTime = request.openTime,
                closeTime = request.closeTime,
                isClosed = request.isClosed
            )
        }
        
        return businessHoursRepository.saveAll(businessHoursList)
    }
    
    /**
     * 특정 요일 영업시간 수정
     */
    @Transactional
    fun updateDayHours(
        storeId: UUID, 
        dayOfWeek: DayOfWeek,
        openTime: LocalTime,
        closeTime: LocalTime,
        isClosed: Boolean = false
    ): BusinessHours {
        val store = storeRepository.findById(storeId)
            .orElseThrow { ResourceNotFoundException("Store not found with id: $storeId") }
        
        val businessHours = businessHoursRepository.findByStoreIdAndDayOfWeek(storeId, dayOfWeek)
            ?: BusinessHours(
                store = store,
                dayOfWeek = dayOfWeek,
                openTime = openTime,
                closeTime = closeTime,
                isClosed = isClosed
            )
        
        if (isClosed) {
            businessHours.markAsClosed()
        } else {
            businessHours.updateHours(openTime, closeTime)
        }
        
        return businessHoursRepository.save(businessHours)
    }
    
    /**
     * 영업장의 모든 영업시간 조회
     */
    fun getBusinessHours(storeId: UUID): List<BusinessHours> {
        return businessHoursRepository.findByStoreId(storeId)
    }
    
    /**
     * 특정 요일 영업시간 조회
     */
    fun getDayHours(storeId: UUID, dayOfWeek: DayOfWeek): BusinessHours? {
        return businessHoursRepository.findByStoreIdAndDayOfWeek(storeId, dayOfWeek)
    }
    
    /**
     * 영업일 목록 조회 (휴무일 제외)
     */
    fun getOpenDays(storeId: UUID): List<BusinessHours> {
        return businessHoursRepository.findByStoreIdAndIsClosedFalse(storeId)
    }
}
