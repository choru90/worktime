package com.worktime

import com.fasterxml.jackson.databind.ObjectMapper
import com.worktime.auth.security.JwtTokenProvider
import com.worktime.domain.model.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime

/** Real HTTP filters/controllers/services/JPA, synthetic records, rolled back after each test. */
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:wt001;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
    "jwt.secret=wt001-local-synthetic-test-key-with-at-least-32-bytes",
    "jwt.expiration=3600000"
])
@AutoConfigureMockMvc
@Transactional
class WorktimeFlowIntegrationTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var em: EntityManager
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var jwt: JwtTokenProvider

    private fun persist(value: Any) { em.persist(value) }
    private fun user(role: UserRole): User = User(
        email = "${java.util.UUID.randomUUID()}@example.test", password = "synthetic",
        name = role.name, role = role
    ).also(::persist)
    private fun token(user: User) = "Bearer ${jwt.generateToken(user.id, user.role)}"

    @Test
    fun `worker submits owner confirms and payroll uses historical wage`() {
        val ownerUser = user(UserRole.OWNER)
        val owner = Owner(user = ownerUser).also(::persist)
        val employee = user(UserRole.WORKER)
        val worker = Worker(name = "Synthetic", phoneNumber = "00000000000",
            owner = owner, userAccount = employee).also(::persist)
        val start = OffsetDateTime.parse("2025-06-10T01:00:00Z")
        val schedule = Schedule(worker = worker, startAtUtc = start,
            endAtUtc = start.plusHours(4), timezone = "Asia/Seoul",
            status = ScheduleStatus.CONFIRMED).also(::persist)
        WageContract(worker = worker, hourlyWage = BigDecimal("10000"),
            startDate = java.time.LocalDate.of(2025, 1, 1),
            endDate = java.time.LocalDate.of(2025, 6, 30)).also(::persist)
        WageContract(worker = worker, hourlyWage = BigDecimal("12000"),
            startDate = java.time.LocalDate.of(2025, 7, 1)).also(::persist)
        em.flush()

        val submitted = mvc.perform(post("/api/worker/schedules/${schedule.id}/worklog")
            .header("Authorization", token(employee)).contentType(MediaType.APPLICATION_JSON)
            .content("""{"clockInAt":"$start","clockOutAt":"${start.plusHours(4)}"}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val record = json.readTree(submitted)
        assertEquals("COMPLETED", record["status"].asText())
        val beforeApproval = mvc.perform(get("/api/payroll/owner/monthly")
            .param("year", "2025").param("month", "6")
            .header("Authorization", token(ownerUser))).andExpect(status().isOk)
            .andReturn().response.contentAsString
        // Characterization of an existing defect, not the desired payroll acceptance policy.
        assertEquals(0, BigDecimal("40000").compareTo(json.readTree(beforeApproval)[0]["totalPay"].decimalValue()))
        mvc.perform(post("/api/work-logs/${record["id"].asText()}/confirm")
            .header("Authorization", token(ownerUser))).andExpect(status().isOk)
        val payroll = mvc.perform(get("/api/payroll/owner/monthly")
            .param("year", "2025").param("month", "6")
            .header("Authorization", token(ownerUser))).andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertEquals(0, BigDecimal("40000").compareTo(json.readTree(payroll)[0]["totalPay"].decimalValue()))
        mvc.perform(post("/api/work-logs/${record["id"].asText()}/reject")
            .header("Authorization", token(employee))).andExpect(status().isInternalServerError)
        // Existing advice maps AccessDeniedException to 500, not 403. Recorded for follow-up.
        assertEquals(WorkLogStatus.CONFIRMED, em.find(WorkLog::class.java,
            java.util.UUID.fromString(record["id"].asText())).status)
    }

    @Test
    fun `rejected work is excluded from payroll`() {
        val ownerUser = user(UserRole.OWNER)
        val owner = Owner(user = ownerUser).also(::persist)
        val worker = Worker(name = "Rejected", phoneNumber = "00000000000", owner = owner).also(::persist)
        WorkLog(worker = worker, clockInAt = OffsetDateTime.parse("2025-06-10T01:00:00Z"),
            clockOutAt = OffsetDateTime.parse("2025-06-10T05:00:00Z"),
            status = WorkLogStatus.REJECTED).also(::persist)
        em.flush()
        val response = mvc.perform(get("/api/payroll/owner/monthly")
            .param("year", "2025").param("month", "6")
            .header("Authorization", token(ownerUser))).andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertEquals(0, json.readTree(response)[0]["totalPay"].decimalValue().compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `characterization - unrelated owner can currently confirm another owners record`() {
        val owner = Owner(user = user(UserRole.OWNER)).also(::persist)
        val unrelated = user(UserRole.OWNER)
        Owner(user = unrelated).also(::persist)
        val worker = Worker(name = "Private", phoneNumber = "00000000000", owner = owner).also(::persist)
        val log = WorkLog(worker = worker,
            clockInAt = OffsetDateTime.parse("2025-06-10T01:00:00Z"),
            clockOutAt = OffsetDateTime.parse("2025-06-10T05:00:00Z"),
            status = WorkLogStatus.COMPLETED).also(::persist)
        em.flush()
        // Existing behavior is unsafe; this test provides reproducible evidence for a scoped fix.
        mvc.perform(post("/api/work-logs/${log.id}/confirm")
            .header("Authorization", token(unrelated))).andExpect(status().isOk)
        assertEquals(WorkLogStatus.CONFIRMED, log.status)
    }
}
