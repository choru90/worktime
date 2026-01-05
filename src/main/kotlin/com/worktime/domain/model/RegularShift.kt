package com.worktime.domain.model

import jakarta.persistence.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

/**
 * RegularShift (고정 근무) 엔티티
 * 요일별 고정 알바 배정을 나타냅니다.
 * 하루를 오전/오후 등으로 나누어 여러 고정 근무를 설정할 수 있습니다.
 * 
 * 예시:
 * - 월요일 오전 (09:00~14:00): 박알바
 * - 월요일 오후 (14:00~20:00): 김알바
 */
@Entity
@Table(
    name = "regular_shifts",
    indexes = [
        Index(name = "idx_regular_shift_store", columnList = "store_id"),
        Index(name = "idx_regular_shift_worker", columnList = "worker_id"),
        Index(name = "idx_regular_shift_day", columnList = "day_of_week"),
        Index(name = "idx_regular_shift_active", columnList = "active")
    ]
)
class RegularShift(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    val id: UUID = UUID.randomUUID(),
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = ForeignKey(name = "fk_regular_shift_store"))
    val store: Store,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false, foreignKey = ForeignKey(name = "fk_regular_shift_worker"))
    var worker: Worker,
    
    /**
     * 요일 (MONDAY ~ SUNDAY)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    val dayOfWeek: DayOfWeek,
    
    /**
     * 근무 시작 시간 (예: 09:00)
     */
    @Column(name = "start_time", nullable = false)
    var startTime: LocalTime,
    
    /**
     * 근무 종료 시간 (예: 14:00 - 오전 근무)
     */
    @Column(name = "end_time", nullable = false)
    var endTime: LocalTime,
    
    /**
     * 근무 이름 (예: "오전근무", "오후근무", "풀타임")
     */
    @Column(name = "shift_name", length = 50)
    var shiftName: String? = null,
    
    /**
     * 활성 상태
     */
    @Column(nullable = false)
    var active: Boolean = true,
    
    /**
     * 유효 시작일 (null이면 즉시 적용)
     */
    @Column(name = "effective_from")
    var effectiveFrom: LocalDate? = null,
    
    /**
     * 유효 종료일 (null이면 무기한)
     */
    @Column(name = "effective_until")
    var effectiveUntil: LocalDate? = null
) : BaseEntity() {
    
    /**
     * 특정 날짜에 이 고정 근무가 유효한지 확인합니다
     */
    fun isEffectiveOn(date: LocalDate): Boolean {
        if (!active) return false
        
        // 해당 요일인지 확인
        if (date.dayOfWeek != dayOfWeek) return false
        
        // 유효 기간 확인
        val afterStart = effectiveFrom == null || !date.isBefore(effectiveFrom)
        val beforeEnd = effectiveUntil == null || !date.isAfter(effectiveUntil)
        
        return afterStart && beforeEnd
    }
    
    /**
     * 고정 근무 시간을 업데이트합니다
     */
    fun updateTime(startTime: LocalTime, endTime: LocalTime) {
        require(startTime.isBefore(endTime)) {
            "시작 시간은 종료 시간보다 이전이어야 합니다"
        }
        this.startTime = startTime
        this.endTime = endTime
    }
    
    /**
     * 담당 근무자를 변경합니다
     */
    fun assignWorker(newWorker: Worker) {
        this.worker = newWorker
    }
    
    /**
     * 고정 근무를 비활성화합니다
     */
    fun deactivate() {
        this.active = false
    }
    
    /**
     * 고정 근무를 활성화합니다
     */
    fun activate() {
        this.active = true
    }
    
    /**
     * 유효 기간을 설정합니다
     */
    fun setEffectivePeriod(from: LocalDate?, until: LocalDate?) {
        if (from != null && until != null) {
            require(!from.isAfter(until)) {
                "시작일은 종료일보다 이전이어야 합니다"
            }
        }
        this.effectiveFrom = from
        this.effectiveUntil = until
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RegularShift) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "RegularShift(id=$id, dayOfWeek=$dayOfWeek, startTime=$startTime, endTime=$endTime, shiftName=$shiftName, active=$active)"
    }
}
