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
        assertEquals(0, BigDecimal.ZERO.compareTo(json.readTree(beforeApproval)[0]["totalPay"].decimalValue()))
        val dashboardBefore = mvc.perform(get("/api/owner/dashboard/summary?date=2025-06-10")
            .header("Authorization", token(ownerUser))).andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertEquals(0, json.readTree(dashboardBefore)["month"]["accruedWage"].decimalValue().compareTo(BigDecimal.ZERO))
        val rankingBefore = mvc.perform(get("/api/owner/dashboard/top-workers?from=2025-06-01&to=2025-06-30")
            .header("Authorization", token(ownerUser))).andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertEquals(0, json.readTree(rankingBefore)["items"][0]["stats"]["accruedWage"].decimalValue().compareTo(BigDecimal.ZERO))
        mvc.perform(post("/api/work-logs/${record["id"].asText()}/confirm")
            .header("Authorization", token(ownerUser))).andExpect(status().isOk)
        val payroll = mvc.perform(get("/api/payroll/owner/monthly")
            .param("year", "2025").param("month", "6")
            .header("Authorization", token(ownerUser))).andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertEquals(0, BigDecimal("40000").compareTo(json.readTree(payroll)[0]["totalPay"].decimalValue()))
        val dashboardAfter = mvc.perform(get("/api/owner/dashboard/summary?date=2025-06-10")
            .header("Authorization", token(ownerUser))).andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertEquals(0, BigDecimal("40000").compareTo(json.readTree(dashboardAfter)["month"]["accruedWage"].decimalValue()))
        val rankingAfter = mvc.perform(get("/api/owner/dashboard/top-workers?from=2025-06-01&to=2025-06-30")
            .header("Authorization", token(ownerUser))).andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertEquals(0, BigDecimal("40000").compareTo(json.readTree(rankingAfter)["items"][0]["stats"]["accruedWage"].decimalValue()))
        mvc.perform(post("/api/work-logs/${record["id"].asText()}/reject")
            .header("Authorization", token(employee))).andExpect(status().isForbidden)
        assertEquals(WorkLogStatus.CONFIRMED, em.find(WorkLog::class.java,
            java.util.UUID.fromString(record["id"].asText())).status)
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = WorkLogStatus::class,
        names = ["COMPLETED", "REJECTED", "ONGOING", "NO_SHOW", "MANUAL_ADJUSTED"])
    fun `unconfirmed states are excluded from every payroll view`(state: WorkLogStatus) {
        val ownerUser = user(UserRole.OWNER)
        val owner = Owner(user = ownerUser).also(::persist)
        val worker = Worker(name = "Rejected", phoneNumber = "00000000000", owner = owner).also(::persist)
        WorkLog(worker = worker, clockInAt = OffsetDateTime.parse("2025-06-10T01:00:00Z"),
            clockOutAt = OffsetDateTime.parse("2025-06-10T05:00:00Z"),
            status = state).also(::persist)
        em.flush()
        val response = mvc.perform(get("/api/payroll/owner/monthly")
            .param("year", "2025").param("month", "6")
            .header("Authorization", token(ownerUser))).andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertEquals(0, json.readTree(response)[0]["totalPay"].decimalValue().compareTo(BigDecimal.ZERO))
        val detail = mvc.perform(get("/api/payroll/workers/${worker.id}/monthly")
            .param("year", "2025").param("month", "6")
            .header("Authorization", token(ownerUser))).andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertEquals(0, json.readTree(detail)["totalAmount"].decimalValue().compareTo(BigDecimal.ZERO))
        val direct = mvc.perform(get("/api/workers/${worker.id}/payroll")
            .param("from", "2025-06-01").param("to", "2025-06-30")
            .header("Authorization", token(ownerUser))).andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertEquals(0, json.readTree(direct)["totalPay"].decimalValue().compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `unrelated owner cannot confirm another owners record`() {
        val owner = Owner(user = user(UserRole.OWNER)).also(::persist)
        val unrelated = user(UserRole.OWNER)
        Owner(user = unrelated).also(::persist)
        val worker = Worker(name = "Private", phoneNumber = "00000000000", owner = owner).also(::persist)
        val log = WorkLog(worker = worker,
            clockInAt = OffsetDateTime.parse("2025-06-10T01:00:00Z"),
            clockOutAt = OffsetDateTime.parse("2025-06-10T05:00:00Z"),
            status = WorkLogStatus.COMPLETED).also(::persist)
        em.flush()
        mvc.perform(post("/api/work-logs/${log.id}/confirm")
            .header("Authorization", token(unrelated))).andExpect(status().isNotFound)
        mvc.perform(post("/api/work-logs/${log.id}/reject")
            .header("Authorization", token(unrelated))).andExpect(status().isNotFound)
        for (path in listOf("/api/work-logs/${log.id}", "/api/workers/${worker.id}/work-logs",
            "/api/payroll/workers/${worker.id}/monthly?year=2025&month=6",
            "/api/workers/${worker.id}/payroll?from=2025-06-01&to=2025-06-30",
            "/api/workers/${worker.id}/work-summary?from=2025-06-01&to=2025-06-30")) {
            mvc.perform(get(path).header("Authorization", token(unrelated))).andExpect(status().isNotFound)
        }
        assertEquals(WorkLogStatus.COMPLETED, log.status)
    }

    @Test
    fun `lists include only owners workers and no show schedules`() {
        val ownerUser = user(UserRole.OWNER)
        val owner = Owner(user = ownerUser).also(::persist)
        val other = Owner(user = user(UserRole.OWNER)).also(::persist)
        val start = OffsetDateTime.parse("2025-06-10T01:00:00Z")
        val workers = listOf(owner, other).map { employer ->
            Worker(name = "Synthetic", phoneNumber = "00000000000", owner = employer).also(::persist)
        }
        workers.forEach { employee ->
            WorkLog(worker = employee, clockInAt = start, clockOutAt = start.plusHours(4),
                status = WorkLogStatus.COMPLETED).also(::persist)
            Schedule(worker = employee, startAtUtc = start.plusDays(1),
                endAtUtc = start.plusDays(1).plusHours(4), timezone = "Asia/Seoul",
                status = ScheduleStatus.CONFIRMED).also(::persist)
        }
        em.flush()
        val response = mvc.perform(get("/api/work-logs")
            .param("from", "2025-06-01T00:00:00Z").param("to", "2025-06-30T23:59:59Z")
            .header("Authorization", token(ownerUser))).andExpect(status().isOk)
            .andReturn().response.contentAsString
        val records = json.readTree(response)
        assertEquals(2, records.size())
        assertTrue(records.all { it["workerId"].asText() == workers[0].id.toString() })
        mvc.perform(get("/api/work-logs").param("workerId", workers[1].id.toString())
            .header("Authorization", token(ownerUser))).andExpect(status().isNotFound)
    }

    @Test
    fun `employee cannot read another employee or respond to their schedules`() {
        val owner = Owner(user = user(UserRole.OWNER)).also(::persist)
        val employee = user(UserRole.WORKER)
        Worker(name = "Caller", phoneNumber = "00000000000", owner = owner, userAccount = employee).also(::persist)
        val target = Worker(name = "Private", phoneNumber = "00000000000", owner = owner,
            userAccount = user(UserRole.WORKER)).also(::persist)
        val start = OffsetDateTime.parse("2025-06-10T01:00:00Z")
        val schedule = Schedule(worker = target, startAtUtc = start, endAtUtc = start.plusHours(4),
            timezone = "Asia/Seoul", status = ScheduleStatus.PROPOSED_BY_OWNER).also(::persist)
        em.flush()
        for (prefix in listOf("/api/worker/schedules", "/api/schedules")) {
            for (action in listOf("confirm", "reject")) {
                mvc.perform(post("$prefix/${schedule.id}/$action")
                    .header("Authorization", token(employee))).andExpect(status().isNotFound)
            }
        }
        mvc.perform(get("/api/workers/${target.id}/work-logs")
            .header("Authorization", token(employee))).andExpect(status().isNotFound)
        mvc.perform(get("/api/workers/${target.id}/payroll?from=2025-06-01&to=2025-06-30")
            .header("Authorization", token(employee))).andExpect(status().isNotFound)
        assertEquals(ScheduleStatus.PROPOSED_BY_OWNER, schedule.status)
    }

    @Test
    fun `employee accepts own proposal but cannot self approve own request`() {
        val owner = Owner(user = user(UserRole.OWNER)).also(::persist)
        val employee = user(UserRole.WORKER)
        val worker = Worker(name = "Caller", phoneNumber = "00000000000", owner = owner,
            userAccount = employee).also(::persist)
        val start = OffsetDateTime.parse("2025-06-10T01:00:00Z")
        val proposal = Schedule(worker = worker, startAtUtc = start, endAtUtc = start.plusHours(4),
            timezone = "Asia/Seoul", status = ScheduleStatus.PROPOSED_BY_OWNER).also(::persist)
        val request = Schedule(worker = worker, startAtUtc = start.plusDays(1), endAtUtc = start.plusDays(1).plusHours(4),
            timezone = "Asia/Seoul", status = ScheduleStatus.REQUESTED_BY_WORKER).also(::persist)
        em.flush()
        mvc.perform(post("/api/worker/schedules/${proposal.id}/confirm")
            .header("Authorization", token(employee))).andExpect(status().isOk)
        mvc.perform(post("/api/schedules/${request.id}/confirm")
            .header("Authorization", token(employee))).andExpect(status().isForbidden)
        mvc.perform(post("/api/schedules/${request.id}/confirm")
            .header("Authorization", token(owner.user))).andExpect(status().isOk)
        assertEquals(ScheduleStatus.CONFIRMED, request.status)
    }

    @Test
    fun `anonymous requests cannot read or change work records`() {
        mvc.perform(get("/api/work-logs")).andExpect(status().isForbidden)
        mvc.perform(post("/api/work-logs/${java.util.UUID.randomUUID()}/confirm"))
            .andExpect(status().isForbidden)
    }
}
