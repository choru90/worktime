package com.worktime.domain.model

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.*

/**
 * Schedule (근무 스케줄) 엔티티
 * 근로자가 요청하거나 고용주가 제안할 수 있는 근무 스케줄을 나타냅니다.
 * 
 * 시간은 UTC로 저장되며(startAtUtc, endAtUtc) 원본 타임존은
 * 참조를 위해 timezone 필드에 보존됩니다.
 */
@Entity
@Table(
    name = "schedules",
    indexes = [
        Index(name = "idx_schedule_worker", columnList = "worker_id"),
        Index(name = "idx_schedule_time", columnList = "start_at_utc,end_at_utc"),
        Index(name = "idx_schedule_status", columnList = "status")
    ]
)
class Schedule(
    @Id
    @Column
    val id: UUID = UUID.randomUUID(),
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false, foreignKey = ForeignKey(name = "fk_schedule_worker"))
    val worker: Worker,
    
    /**
     * 스케줄이 속한 영업장
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", foreignKey = ForeignKey(name = "fk_schedule_store"))
    val store: Store? = null,
    
    /**
     * 대체 근무인 경우, 어떤 고정 근무를 대체하는지
     * null이면 추가 근무 요청
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacing_regular_shift_id", foreignKey = ForeignKey(name = "fk_schedule_regular_shift"))
    val replacingRegularShift: RegularShift? = null,
    
    /**
     * UTC 기준 스케줄 시작 시간
     * datetime(6)으로 저장됩니다
     */
    @Column(nullable = false)
    var startAtUtc: OffsetDateTime,
    
    /**
     * UTC 기준 스케줄 종료 시간
     * datetime(6)으로 저장됩니다
     */
    @Column(nullable = false)
    var endAtUtc: OffsetDateTime,
    
    /**
     * 원본 타임존 식별자 (예: "Asia/Seoul")
     * 참조 및 표시 목적으로 보존됩니다
     */
    @Column(nullable = false, length = 50)
    var timezone: String,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ScheduleStatus = ScheduleStatus.REQUESTED_BY_WORKER
) : BaseEntity() {
    
    /**
     * 스케줄을 확정합니다
     */
    fun confirm() {
        require(status == ScheduleStatus.REQUESTED_BY_WORKER || status == ScheduleStatus.PROPOSED_BY_OWNER) {
            "REQUESTED_BY_WORKER 또는 PROPOSED_BY_OWNER 상태의 스케줄만 확정할 수 있습니다"
        }
        status = ScheduleStatus.CONFIRMED
    }
    
    /**
     * 스케줄을 거부합니다
     */
    fun reject() {
        require(status == ScheduleStatus.REQUESTED_BY_WORKER || status == ScheduleStatus.PROPOSED_BY_OWNER) {
            "REQUESTED_BY_WORKER 또는 PROPOSED_BY_OWNER 상태의 스케줄만 거부할 수 있습니다"
        }
        status = ScheduleStatus.REJECTED
    }
    
    /**
     * 스케줄을 취소합니다
     */
    fun cancel() {
        require(status == ScheduleStatus.CONFIRMED) {
            "확정된 스케줄만 취소할 수 있습니다"
        }
        status = ScheduleStatus.CANCELED
    }

    /**
     * 스케줄 시간을 수정합니다
     */
    fun updateTime(startAtUtc: OffsetDateTime, endAtUtc: OffsetDateTime, timezone: String) {
        require(endAtUtc.isAfter(startAtUtc)) {
            "종료 시간은 시작 시간보다 이후여야 합니다"
        }
        this.startAtUtc = startAtUtc
        this.endAtUtc = endAtUtc
        this.timezone = timezone
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Schedule) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "Schedule(id=$id, startAtUtc=$startAtUtc, endAtUtc=$endAtUtc, timezone='$timezone', status=$status)"
    }
}
