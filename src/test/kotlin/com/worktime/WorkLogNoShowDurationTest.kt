package com.worktime

import com.worktime.domain.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Duration
import java.time.OffsetDateTime

/** WT-001: additional negative and null-state boundaries for work logs. */
class WorkLogNoShowDurationTest {
    private val start = OffsetDateTime.parse("2025-12-31T23:30:00Z")
    private fun log(status: WorkLogStatus = WorkLogStatus.ONGOING, end: OffsetDateTime? = null): WorkLog {
        val owner = Owner(user = User(email = "owner@example.test", password = "synthetic",
            name = "Owner", role = UserRole.OWNER))
        return WorkLog(worker = Worker(name = "Synthetic", phoneNumber = "00000000000", owner = owner),
            clockInAt = start, clockOutAt = end, status = status)
    }

    @Test
    fun `ongoing work has no duration before clock out`() {
        val record = log()
        assertNull(record.calculateDuration())
        assertTrue(record.isOngoing())
    }

    @Test
    fun `no show transitions once and cannot be approved or rejected`() {
        val record = log()
        record.markAsNoShow()
        assertEquals(WorkLogStatus.NO_SHOW, record.status)
        assertFalse(record.isOngoing())
        assertNull(record.clockOutAt)
        assertThrows(IllegalArgumentException::class.java) { record.markAsNoShow() }
        assertThrows(IllegalArgumentException::class.java) { record.confirm() }
        assertThrows(IllegalArgumentException::class.java) { record.reject() }
        assertEquals(WorkLogStatus.NO_SHOW, record.status)
    }

    @ParameterizedTest
    @EnumSource(value = WorkLogStatus::class, names = ["COMPLETED", "CONFIRMED", "REJECTED", "NO_SHOW", "MANUAL_ADJUSTED"])
    fun `non ongoing work cannot be marked no show`(status: WorkLogStatus) {
        val record = log(status)
        assertThrows(IllegalArgumentException::class.java) { record.markAsNoShow() }
        assertEquals(status, record.status)
    }

    @Test
    fun `duration uses instants even when offsets differ`() {
        val end = OffsetDateTime.parse("2026-01-01T10:30:00+09:00")
        val record = log(WorkLogStatus.COMPLETED, end)
        assertEquals(Duration.ofHours(2), record.calculateDuration())
    }

    @Test
    fun `manual adjustment replaces timestamps and marks pending approval`() {
        val record = log(WorkLogStatus.COMPLETED, start.plusHours(1))
        val adjustedStart = start.minusMinutes(30)
        val adjustedEnd = start.plusHours(3)
        record.manuallyAdjust(adjustedStart, adjustedEnd)
        assertEquals(adjustedStart, record.clockInAt)
        assertEquals(adjustedEnd, record.clockOutAt)
        assertEquals(Duration.ofMinutes(210), record.calculateDuration())
        assertEquals(WorkLogStatus.MANUAL_ADJUSTED, record.status)
        assertThrows(IllegalArgumentException::class.java) { record.confirm() }
    }

    @Test
    fun `manual adjustment with no end leaves duration undefined`() {
        val record = log(WorkLogStatus.COMPLETED, start.plusHours(1))
        record.manuallyAdjust(start.plusMinutes(10), null)
        assertNull(record.clockOutAt)
        assertNull(record.calculateDuration())
        assertFalse(record.isOngoing())
        assertEquals(WorkLogStatus.MANUAL_ADJUSTED, record.status)
    }
}
