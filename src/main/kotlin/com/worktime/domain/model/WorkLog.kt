package com.worktime.domain.model

import jakarta.persistence.*
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

/**
 * Work log (출퇴근 기록) entity.
 * Represents a worker's clock-in and clock-out record.
 * 
 * Times are stored in UTC with timezone information.
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
    @Column(columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false, foreignKey = ForeignKey(name = "fk_work_log_worker"))
    val worker: Worker,
    
    /**
     * Associated schedule (optional - can be null for ad-hoc work)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = true, foreignKey = ForeignKey(name = "fk_work_log_schedule"))
    val schedule: Schedule? = null,
    
    /**
     * Clock-in time in UTC
     * Stored as TIMESTAMP WITH TIME ZONE
     */
    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    var clockInAt: OffsetDateTime,
    
    /**
     * Clock-out time in UTC (nullable - null means still working)
     * Stored as TIMESTAMP WITH TIME ZONE
     */
    @Column(nullable = true, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    var clockOutAt: OffsetDateTime? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: WorkLogStatus = WorkLogStatus.ONGOING
) : BaseEntity() {
    
    /**
     * Clock out the worker
     */
    fun clockOut(clockOutTime: OffsetDateTime) {
        require(status == WorkLogStatus.ONGOING) {
            "Can only clock out for ongoing work logs"
        }
        require(clockOutTime.isAfter(clockInAt)) {
            "Clock out time must be after clock in time"
        }
        clockOutAt = clockOutTime
        status = WorkLogStatus.COMPLETED
    }
    
    /**
     * Mark as no-show
     */
    fun markAsNoShow() {
        require(status == WorkLogStatus.ONGOING) {
            "Can only mark ongoing work logs as no-show"
        }
        status = WorkLogStatus.NO_SHOW
    }
    
    /**
     * Manually adjust the work log
     */
    fun manuallyAdjust(newClockInAt: OffsetDateTime, newClockOutAt: OffsetDateTime?) {
        clockInAt = newClockInAt
        clockOutAt = newClockOutAt
        status = WorkLogStatus.MANUAL_ADJUSTED
    }
    
    /**
     * Calculate worked duration (if clocked out)
     */
    fun calculateDuration(): Duration? {
        return clockOutAt?.let { Duration.between(clockInAt, it) }
    }
    
    /**
     * Check if worker is currently working
     */
    fun isOngoing(): Boolean {
        return status == WorkLogStatus.ONGOING && clockOutAt == null
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
