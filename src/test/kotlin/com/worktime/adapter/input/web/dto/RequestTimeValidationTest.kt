package com.worktime.adapter.input.web.dto

import com.worktime.adapter.input.web.dto.schedule.*
import com.worktime.adapter.input.web.dto.worklog.ManualWorkLogRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import java.util.UUID

class RequestTimeValidationTest {
    @Test fun `schedule and manual work requests reject equal and reversed ranges at construction`() {
        val start=OffsetDateTime.parse("2024-01-01T09:00:00Z")
        for(end in listOf(start,start.minusSeconds(1))) {
            assertThrows<IllegalArgumentException> { ScheduleCreateRequest(UUID.randomUUID(),start,end,"UTC",ScheduleRequestType.WORKER_REQUEST) }
            assertThrows<IllegalArgumentException> { ScheduleUpdateRequest(start,end,"UTC") }
            assertThrows<IllegalArgumentException> { ManualWorkLogRequest(start,end) }
        }
        assertEquals(start.plusSeconds(1),ManualWorkLogRequest(start,start.plusSeconds(1)).clockOutAt)
        assertNull(com.worktime.adapter.input.web.dto.worklog.ClockInRequest(clockInAt=start).scheduleId)
    }
}
