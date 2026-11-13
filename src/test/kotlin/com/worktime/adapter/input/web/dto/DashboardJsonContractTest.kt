package com.worktime.adapter.input.web.dto

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.worktime.adapter.input.web.dto.dashboard.*
import com.worktime.domain.model.DashboardActionType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

class DashboardJsonContractTest {
    private val json=jacksonObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val id=UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val time=OffsetDateTime.parse("2024-02-01T09:00:00Z")
    @Test fun `legacy action DTO preserves all documented JSON field names and enum values`() {
        for(severity in ActionSeverity.values()) {
            val action=DashboardActionResponse(id,DashboardActionType.MISSING_CHECKOUT,"Missing checkout","Shift still ongoing","Worker",time,severity)
            val expected=json.readTree("""{"id":"$id","type":"MISSING_CHECKOUT","title":"Missing checkout","description":"Shift still ongoing","workerName":"Worker","createdAt":"2024-02-01T09:00:00Z","severity":"${severity.name}"}""")
            assertEquals(expected,json.readTree(json.writeValueAsString(action)))
            assertEquals(action,json.readValue(json.writeValueAsString(action),DashboardActionResponse::class.java))
        }
    }
    @Test fun `attendance exception JSON retains nullable fields and offset timestamps`() {
        val minimal=AttendanceExceptionDto(type=AttendanceExceptionType.CHECKIN_EXISTS_BUT_NO_CHECKOUT,workerId=id,workerName="Worker")
        val expected=json.readTree("""{"type":"CHECKIN_EXISTS_BUT_NO_CHECKOUT","scheduleId":null,"workLogId":null,"workerId":"$id","workerName":"Worker","scheduledStartAt":null,"scheduledEndAt":null,"checkedInAt":null,"checkedOutAt":null}""")
        assertEquals(expected,json.readTree(json.writeValueAsString(minimal)))
        val full=minimal.copy(scheduleId=id,workLogId=id,scheduledStartAt=time,scheduledEndAt=time.plusHours(1),checkedInAt=time,checkedOutAt=time.plusMinutes(55))
        assertEquals(full,json.readValue(json.writeValueAsString(full),AttendanceExceptionDto::class.java))
    }
}
