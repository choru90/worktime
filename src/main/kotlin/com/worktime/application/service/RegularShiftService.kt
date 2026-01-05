package com.worktime.application.service

import com.worktime.adapter.output.persistence.RegularShiftJpaRepository
import com.worktime.adapter.output.persistence.StoreJpaRepository
import com.worktime.adapter.output.persistence.WorkerJpaRepository
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.model.RegularShift
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

/**
 * RegularShift (고정근무) 관리 서비스
 */
@Service
@Transactional(readOnly = true)
class RegularShiftService(
    private val regularShiftRepository: RegularShiftJpaRepository,
    private val storeRepository: StoreJpaRepository,
    private val workerRepository: WorkerJpaRepository
) {
    
    /**
     * 고정근무 생성
     */
    @Transactional
    fun createRegularShift(
        storeId: UUID,
        workerId: UUID,
        dayOfWeek: DayOfWeek,
        startTime: LocalTime,
        endTime: LocalTime,
        shiftName: String? = null,
        effectiveFrom: LocalDate? = null,
        effectiveUntil: LocalDate? = null
    ): RegularShift {
        val store = storeRepository.findById(storeId)
            .orElseThrow { ResourceNotFoundException("Store not found with id: $storeId") }
        
        val worker = workerRepository.findById(workerId)
            .orElseThrow { ResourceNotFoundException("Worker not found with id: $workerId") }
        
        require(startTime.isBefore(endTime)) {
            "시작 시간은 종료 시간보다 이전이어야 합니다"
        }
        
        val regularShift = RegularShift(
            store = store,
            worker = worker,
            dayOfWeek = dayOfWeek,
            startTime = startTime,
            endTime = endTime,
            shiftName = shiftName,
            effectiveFrom = effectiveFrom,
            effectiveUntil = effectiveUntil
        )
        
        return regularShiftRepository.save(regularShift)
    }
    
    /**
     * 영업장의 모든 고정근무 조회
     */
    fun getRegularShiftsByStore(storeId: UUID): List<RegularShift> {
        return regularShiftRepository.findByStoreId(storeId)
    }
    
    /**
     * 영업장의 활성 고정근무 조회
     */
    fun getActiveRegularShiftsByStore(storeId: UUID): List<RegularShift> {
        return regularShiftRepository.findByStoreIdAndActiveTrue(storeId)
    }
    
    /**
     * 특정 요일의 고정근무 조회
     */
    fun getRegularShiftsByDay(storeId: UUID, dayOfWeek: DayOfWeek): List<RegularShift> {
        return regularShiftRepository.findByStoreIdAndDayOfWeekAndActiveTrue(storeId, dayOfWeek)
    }
    
    /**
     * 특정 날짜에 유효한 고정근무 조회
     * (해당 날짜의 요일 + 유효기간 체크)
     */
    fun getEffectiveShiftsForDate(storeId: UUID, date: LocalDate): List<RegularShift> {
        return regularShiftRepository.findEffectiveShifts(
            storeId = storeId,
            dayOfWeek = date.dayOfWeek,
            date = date
        )
    }
    
    /**
     * 오늘 근무 스케줄 조회 (고정근무)
     */
    fun getTodayShifts(storeId: UUID): List<RegularShift> {
        return getEffectiveShiftsForDate(storeId, LocalDate.now())
    }
    
    /**
     * Worker의 고정근무 조회
     */
    fun getRegularShiftsByWorker(workerId: UUID): List<RegularShift> {
        return regularShiftRepository.findByWorkerIdAndActiveTrue(workerId)
    }
    
    /**
     * 고정근무 조회
     */
    fun getRegularShift(shiftId: UUID): RegularShift {
        return regularShiftRepository.findById(shiftId)
            .orElseThrow { ResourceNotFoundException("RegularShift not found with id: $shiftId") }
    }
    
    /**
     * 고정근무 시간 수정
     */
    @Transactional
    fun updateShiftTime(
        shiftId: UUID,
        startTime: LocalTime,
        endTime: LocalTime
    ): RegularShift {
        val shift = getRegularShift(shiftId)
        shift.updateTime(startTime, endTime)
        return regularShiftRepository.save(shift)
    }
    
    /**
     * 고정근무 담당자 변경
     */
    @Transactional
    fun assignWorker(shiftId: UUID, workerId: UUID): RegularShift {
        val shift = getRegularShift(shiftId)
        val worker = workerRepository.findById(workerId)
            .orElseThrow { ResourceNotFoundException("Worker not found with id: $workerId") }
        
        shift.assignWorker(worker)
        return regularShiftRepository.save(shift)
    }
    
    /**
     * 고정근무 비활성화
     */
    @Transactional
    fun deactivateShift(shiftId: UUID): RegularShift {
        val shift = getRegularShift(shiftId)
        shift.deactivate()
        return regularShiftRepository.save(shift)
    }
    
    /**
     * 고정근무 유효기간 설정
     */
    @Transactional
    fun setEffectivePeriod(
        shiftId: UUID,
        from: LocalDate?,
        until: LocalDate?
    ): RegularShift {
        val shift = getRegularShift(shiftId)
        shift.setEffectivePeriod(from, until)
        return regularShiftRepository.save(shift)
    }
}
