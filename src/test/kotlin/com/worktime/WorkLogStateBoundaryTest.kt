package com.worktime

import com.worktime.domain.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

/** WT-001 P0: deterministic domain boundaries; no database or production accounts. */
class WorkLogStateBoundaryTest {
    private val start = OffsetDateTime.parse("2025-06-30T23:30:00Z")
    private fun worker(): Worker = Worker(
        name = "Synthetic", phoneNumber = "00000000000",
        owner = Owner(user = User(email = "owner@example.test", password = "synthetic",
            name = "Owner", role = UserRole.OWNER))
    )
    private fun log(status: WorkLogStatus = WorkLogStatus.ONGOING) =
        WorkLog(worker = worker(), clockInAt = start, status = status)

    @Test
    fun `clock out rejects same or earlier instant without changing state`() {
        for (invalid in listOf(start, start.minusNanos(1), start.minusHours(1))) {
            val record = log()
            assertThrows(IllegalArgumentException::class.java) { record.clockOut(invalid) }
            assertEquals(WorkLogStatus.ONGOING, record.status)
            assertNull(record.clockOutAt)
        }
    }

    @Test
    fun `clock out across UTC month boundary retains exact duration`() {
        val record = log()
        record.clockOut(start.plusHours(2))
        assertEquals(Duration.ofHours(2), record.calculateDuration())
        assertEquals(WorkLogStatus.COMPLETED, record.status)
        assertFalse(record.isOngoing())
    }

    @Test
    fun `completed record can be approved exactly once`() {
        val record = log()
        record.clockOut(start.plusMinutes(1))
        record.confirm()
        assertEquals(WorkLogStatus.CONFIRMED, record.status)
        assertThrows(IllegalArgumentException::class.java) { record.confirm() }
        assertThrows(IllegalArgumentException::class.java) { record.reject() }
        assertEquals(WorkLogStatus.CONFIRMED, record.status)
    }

    @Test
    fun `rejected record cannot later be confirmed without correction`() {
        val record = log()
        record.clockOut(start.plusMinutes(1))
        record.reject()
        assertEquals(WorkLogStatus.REJECTED, record.status)
        assertThrows(IllegalArgumentException::class.java) { record.confirm() }
        assertEquals(WorkLogStatus.REJECTED, record.status)
    }

    @ParameterizedTest
    @EnumSource(value = WorkLogStatus::class, names = ["COMPLETED", "CONFIRMED", "REJECTED", "NO_SHOW", "MANUAL_ADJUSTED"])
    fun `non ongoing states reject clock out`(status: WorkLogStatus) {
        val record = log(status)
        assertThrows(IllegalArgumentException::class.java) { record.clockOut(start.plusHours(1)) }
        assertEquals(status, record.status)
    }

    @Test
    fun `wage contract starts and ends inclusively`() {
        val contract = WageContract(worker = worker(), hourlyWage = java.math.BigDecimal("10000"),
            startDate = LocalDate.of(2025, 6, 1), endDate = LocalDate.of(2025, 6, 30))
        assertFalse(contract.isActiveOn(LocalDate.of(2025, 5, 31)))
        assertTrue(contract.isActiveOn(LocalDate.of(2025, 6, 1)))
        assertTrue(contract.isActiveOn(LocalDate.of(2025, 6, 30)))
        assertFalse(contract.isActiveOn(LocalDate.of(2025, 7, 1)))
        assertFalse(contract.isCurrentlyActive())
    }

    @Test
    fun `open ended wage contract remains active on future dates`() {
        val contract = WageContract(worker = worker(), hourlyWage = java.math.BigDecimal("12000"),
            startDate = LocalDate.of(2025, 7, 1))
        assertFalse(contract.isActiveOn(LocalDate.of(2025, 6, 30)))
        assertTrue(contract.isActiveOn(LocalDate.of(2025, 7, 1)))
        assertTrue(contract.isActiveOn(LocalDate.of(2030, 1, 1)))
        assertTrue(contract.isCurrentlyActive())
    }
}
