package com.worktime.domain.model

import jakarta.persistence.*
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

/**
 * Work log (출퇴근 기록) 엔티티
 * 근로자의 출근 및 퇴근 기록을 나타냅니다.
 * 
 * 시간은 타임존 정보와 함께 UTC로 저장됩니다.
 */
@Entity
@Table(
    name = "work_logs",
    indexes = [
        Index(name = "idx_work_log_worker", columnList = "worker_id"),
        Index(name = "idx_work_log_schedule", columnList = "schedule_id"),
        Index(name = "idx_work_log_clock_in", columnList = "clock_in_at"),
        Index(name = "idx_work_log_status", columnList = "status")
    ]
)
class WorkLog(
    @Id
    @Column
    val id: UUID = UUID.randomUUID(),
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false, foreignKey = ForeignKey(name = "fk_work_log_worker"))
    val worker: Worker,
    
    /**
     * 연결된 스케줄 (선택사항 - 임시 근무의 경우 null 가능)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = true, foreignKey = ForeignKey(name = "fk_work_log_schedule"))
    val schedule: Schedule? = null,
    
    /**
     * 연결된 고정 근무 (선택사항 - 고정 근무인 경우)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regular_shift_id", nullable = true, foreignKey = ForeignKey(name = "fk_work_log_regular_shift"))
    val regularShift: RegularShift? = null,
    
    /**
     * UTC 기준 출근 시간
     * datetime(6)으로 저장됩니다
     */
    @Column(nullable = false)
    var clockInAt: OffsetDateTime,
    
    /**
     * UTC 기준 퇴근 시간 (nullable - null은 아직 근무 중을 의미)
     * datetime(6)으로 저장됩니다
     */
    @Column(nullable = true)
    var clockOutAt: OffsetDateTime? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: WorkLogStatus = WorkLogStatus.ONGOING
) : BaseEntity() {
    
    /**
     * 근로자를 퇴근 처리합니다
     */
    fun clockOut(clockOutTime: OffsetDateTime) {
        require(status == WorkLogStatus.ONGOING) {
            "진행 중인 근무 기록만 퇴근 처리할 수 있습니다"
        }
        require(clockOutTime.isAfter(clockInAt)) {
            "퇴근 시간은 출근 시간 이후여야 합니다"
        }
        clockOutAt = clockOutTime
        status = WorkLogStatus.COMPLETED
    }
    
    /**
     * 무단 결근으로 표시합니다
     */
    fun markAsNoShow() {
        require(status == WorkLogStatus.ONGOING) {
            "진행 중인 근무 기록만 무단 결근으로 표시할 수 있습니다"
        }
        status = WorkLogStatus.NO_SHOW
    }
    
    /**
     * 근무 기록을 수동으로 조정합니다
     */
    fun manuallyAdjust(newClockInAt: OffsetDateTime, newClockOutAt: OffsetDateTime?) {
        clockInAt = newClockInAt
        clockOutAt = newClockOutAt
        status = WorkLogStatus.MANUAL_ADJUSTED
    }
    
    /**
     * 근무 시간을 계산합니다 (퇴근 처리된 경우)
     */
    fun calculateDuration(): Duration? {
        return clockOutAt?.let { Duration.between(clockInAt, it) }
    }
    
    /**
     * 근로자가 현재 근무 중인지 확인합니다
     */
    fun isOngoing(): Boolean {
        return status == WorkLogStatus.ONGOING && clockOutAt == null
    }
    
    /**
     * 근무 기록을 확정합니다 (사장이 검수 완료)
     */
    fun confirm() {
        require(status == WorkLogStatus.COMPLETED) {
            "완료된 근무 기록만 확정할 수 있습니다"
        }
        status = WorkLogStatus.CONFIRMED
    }
    
    /**
     * 근무 기록을 반려합니다 (사장이 거절)
     */
    fun reject() {
        require(status == WorkLogStatus.COMPLETED) {
            "완료된 근무 기록만 반려할 수 있습니다"
        }
        status = WorkLogStatus.REJECTED
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WorkLog) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "WorkLog(id=$id, clockInAt=$clockInAt, clockOutAt=$clockOutAt, status=$status)"
    }
}
