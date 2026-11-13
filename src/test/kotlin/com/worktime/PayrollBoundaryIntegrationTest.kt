package com.worktime

import com.fasterxml.jackson.databind.ObjectMapper
import com.worktime.auth.security.JwtTokenProvider
import com.worktime.domain.model.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * WT-001: verifies the existing *estimated* monthly payroll behavior.
 * Uses isolated in-memory records, never production or shared test accounts.
 */
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:wt001_payroll_boundary;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
    "jwt.secret=wt001-local-synthetic-test-key-with-at-least-32-bytes",
    "jwt.expiration=3600000"
])
@AutoConfigureMockMvc
@Transactional
class PayrollBoundaryIntegrationTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var em: EntityManager
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var jwt: JwtTokenProvider

    @Test
    fun `historical hourly rate is used for confirmed work in each month`() {
        val account = User(email = "${UUID.randomUUID()}@example.test", password = "synthetic",
            name = "Owner", role = UserRole.OWNER).also(em::persist)
        val owner = Owner(user = account).also(em::persist)
        val worker = Worker(name = "Boundary", phoneNumber = "00000000000", owner = owner).also(em::persist)
        WageContract(worker = worker, hourlyWage = BigDecimal("10000"),
            startDate = LocalDate.of(2025, 1, 1),
            endDate = LocalDate.of(2025, 6, 30)).also(em::persist)
        WageContract(worker = worker, hourlyWage = BigDecimal("12000"),
            startDate = LocalDate.of(2025, 7, 1)).also(em::persist)
        WorkLog(worker = worker,
            clockInAt = OffsetDateTime.parse("2025-06-10T01:00:00Z"),
            clockOutAt = OffsetDateTime.parse("2025-06-10T05:00:00Z"),
            status = WorkLogStatus.CONFIRMED).also(em::persist)
        WorkLog(worker = worker,
            clockInAt = OffsetDateTime.parse("2025-07-10T01:00:00Z"),
            clockOutAt = OffsetDateTime.parse("2025-07-10T05:00:00Z"),
            status = WorkLogStatus.CONFIRMED).also(em::persist)
        em.flush()
        val token = "Bearer ${jwt.generateToken(account.id, account.role)}"
        fun amount(month: Int): BigDecimal {
            val body = mvc.perform(get("/api/payroll/owner/monthly")
                .param("year", "2025").param("month", month.toString())
                .header("Authorization", token)).andExpect(status().isOk)
                .andReturn().response.contentAsString
            val entry = json.readTree(body).first { it["workerId"].asText() == worker.id.toString() }
            return entry["totalPay"].decimalValue()
        }
        assertEquals(0, BigDecimal("40000").compareTo(amount(6)))
        assertEquals(0, BigDecimal("48000").compareTo(amount(7)))
    }
}
