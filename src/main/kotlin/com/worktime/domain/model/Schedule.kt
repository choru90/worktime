package com.worktime.domain.model

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.*

/**
 * Schedule (근무 스케줄) entity.
 * Represents a work schedule that can be requested by worker or proposed by owner.
 * 
 * Times are stored in UTC (startAtUtc, endAtUtc) while the original timezone
 * is preserved in the timezone field for reference.
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
     * Schedule start time in UTC
     * Stored as TIMESTAMP WITH TIME ZONE
     */
    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    var startAtUtc: OffsetDateTime,
    
    /**
     * Schedule end time in UTC
     * Stored as TIMESTAMP WITH TIME ZONE
     */
    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    var endAtUtc: OffsetDateTime,
    
    /**
     * Original timezone identifier (e.g., "Asia/Seoul")
     * Preserved for reference and display purposes
     */
    @Column(nullable = false, length = 50)
    var timezone: String,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ScheduleStatus = ScheduleStatus.REQUESTED_BY_WORKER
) : BaseEntity() {
    
    /**
     * Confirm the schedule
     */
    fun confirm() {
        require(status == ScheduleStatus.REQUESTED_BY_WORKER || status == ScheduleStatus.PROPOSED_BY_OWNER) {
            "Can only confirm schedules that are in REQUESTED_BY_WORKER or PROPOSED_BY_OWNER status"
        }
        status = ScheduleStatus.CONFIRMED
    }
    
    /**
     * Reject the schedule
     */
    fun reject() {
        require(status == ScheduleStatus.REQUESTED_BY_WORKER || status == ScheduleStatus.PROPOSED_BY_OWNER) {
            "Can only reject schedules that are in REQUESTED_BY_WORKER or PROPOSED_BY_OWNER status"
        }
        status = ScheduleStatus.REJECTED
    }
    
    /**
     * Cancel the schedule
     */
    fun cancel() {
        require(status == ScheduleStatus.CONFIRMED) {
            "Can only cancel confirmed schedules"
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
