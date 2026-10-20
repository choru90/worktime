package com.worktime.domain.model
import jakarta.persistence.*
import java.time.OffsetDateTime
import java.time.Instant
import java.util.UUID
@Entity @Table(name="work_log_corrections")
class WorkLogCorrection(
 @Id val id: UUID = UUID.randomUUID(),
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="work_log_id",nullable=false) val workLog: WorkLog,
 @Column(nullable=false) val previousIn: OffsetDateTime,
 val previousOut: OffsetDateTime?,
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) val previousStatus: WorkLogStatus,
 @Column(nullable=false) val requestedIn: OffsetDateTime,
 @Column(nullable=false) val requestedOut: OffsetDateTime,
 @Column(nullable=false,length=500) val reason: String,
 @Column(length=500) val previousRejectionReason: String? = null,
 @Column(nullable=false) val revision: Int,
 @Column(nullable=false) val requestedAt: Instant = Instant.now()
)
