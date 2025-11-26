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
    @Column(columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false, foreignKey = ForeignKey(name = "fk_schedule_worker"))
    val worker: Worker,
    
    /**
     * UTC 기준 스케줄 시작 시간
     * TIMESTAMP WITH TIME ZONE으로 저장됩니다
     */
    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    var startAtUtc: OffsetDateTime,
    
    /**
     * UTC 기준 스케줄 종료 시간
     * TIMESTAMP WITH TIME ZONE으로 저장됩니다
     */
    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
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
