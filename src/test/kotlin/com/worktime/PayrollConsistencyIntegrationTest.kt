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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:wt002_payroll;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
    "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=WARN",
    "jwt.secret=wt002-synthetic-local-test-key-with-at-least-32-bytes", "jwt.expiration=3600000"
])
@AutoConfigureMockMvc
@Transactional
class PayrollConsistencyIntegrationTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var em: EntityManager
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var jwt: JwtTokenProvider

    @Test fun `persisted split shifts have identical rounded amounts in all payroll APIs`() {
        val account = User(email="${UUID.randomUUID()}@example.test",password="synthetic",name="Owner",role=UserRole.OWNER).also(em::persist)
        val owner = Owner(user=account).also(em::persist)
        val worker = Worker(name="Split shifts",phoneNumber="000",owner=owner).also(em::persist)
        WageContract(worker=worker,hourlyWage=BigDecimal("10000"),startDate=LocalDate.of(2024,1,1),endDate=LocalDate.of(2024,2,28)).also(em::persist)
        WageContract(worker=worker,hourlyWage=BigDecimal("10001"),startDate=LocalDate.of(2024,2,29)).also(em::persist)
        val specs = listOf("2024-02-28T09:00:00Z" to 30L, "2024-02-29T09:00:00Z" to 30L, "2024-02-29T11:00:00Z" to 30L)
        val ids = specs.map { (at, minutes) ->
            val start=OffsetDateTime.parse(at)
            WorkLog(worker=worker,clockInAt=start,clockOutAt=start.plusMinutes(minutes),status=WorkLogStatus.CONFIRMED).also(em::persist).id
        }
        em.flush(); em.clear()
        // Independent arithmetic: 5000 + HALF_UP(5000.5) + HALF_UP(5000.5) = 15002.
        val expected=BigDecimal("15002")
        val auth="Bearer ${jwt.generateToken(account.id,account.role)}"
        fun read(path:String)=json.readTree(mvc.perform(get(path).header("Authorization",auth)).andExpect(status().isOk).andReturn().response.contentAsString)
        val direct=read("/api/workers/${worker.id}/payroll?from=2024-02-01&to=2024-02-29")
        val summary=read("/api/payroll/owner/monthly?year=2024&month=2").single()
        val detail=read("/api/payroll/workers/${worker.id}/monthly?year=2024&month=2")
        assertEquals(0,expected.compareTo(direct["totalPay"].decimalValue()))
        assertEquals(0,expected.compareTo(summary["totalPay"].decimalValue()))
        assertEquals(0,expected.compareTo(detail["totalAmount"].decimalValue()))
        assertEquals(listOf(5000,10002),detail["dailyDetails"].map { it["amount"].asInt() })
        assertEquals(90,detail["totalWorkMinutes"].asInt())
        assertEquals(ids.toSet(),direct["wageDetails"].map { UUID.fromString(it["workLogId"].asText()) }.toSet())
        ids.forEach { id ->
            val saved=em.find(WorkLog::class.java,id)
            assertEquals(WorkLogStatus.CONFIRMED,saved.status)
            assertEquals(30L,saved.calculateDuration()!!.toMinutes())
            assertEquals(0,saved.correctionCount)
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "2024-02-29T23:30:00Z,2024,2,2024-02-29",
        "2024-12-31T23:30:00Z,2024,12,2024-12-31",
        "2025-01-01T00:30:00Z,2025,1,2025-01-01",
        "2024-03-01T00:30:00+09:00,2024,2,2024-02-29"
    )
    fun `persisted calendar boundaries use UTC month and historical rate`(at:String,year:Int,month:Int,wageDay:String) {
        val account=User(email="${UUID.randomUUID()}@example.test",password="synthetic",name="Owner",role=UserRole.OWNER).also(em::persist)
        val owner=Owner(user=account).also(em::persist)
        val worker=Worker(name="Boundary",phoneNumber="000",owner=owner).also(em::persist)
        val date=LocalDate.parse(wageDay)
        WageContract(worker=worker,hourlyWage=BigDecimal("10001"),startDate=date,endDate=date).also(em::persist)
        val start=OffsetDateTime.parse(at)
        val log=WorkLog(worker=worker,clockInAt=start,clockOutAt=start.plusMinutes(30).plusSeconds(59),status=WorkLogStatus.CONFIRMED).also(em::persist)
        em.flush();em.clear()
        val headers="Bearer ${jwt.generateToken(account.id,account.role)}"
        val detail=json.readTree(mvc.perform(get("/api/payroll/workers/${worker.id}/monthly?year=$year&month=$month").header("Authorization",headers)).andExpect(status().isOk).andReturn().response.contentAsString)
        // Seconds are truncated, full shift belongs to its stored UTC clock-in date.
        assertEquals(5001,detail["totalAmount"].asInt())
        assertEquals(30,detail["totalWorkMinutes"].asInt())
        assertEquals(wageDay,detail["dailyDetails"].single()["date"].asText())
        assertEquals(30L,em.find(WorkLog::class.java,log.id).calculateDuration()!!.toMinutes())
        val next=java.time.YearMonth.of(year,month).plusMonths(1)
        val nextDetail=json.readTree(mvc.perform(get("/api/payroll/workers/${worker.id}/monthly?year=${next.year}&month=${next.monthValue}").header("Authorization",headers)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals(0,nextDetail["totalAmount"].asInt())
        val summary=json.readTree(mvc.perform(get("/api/payroll/owner/monthly?year=$year&month=$month").header("Authorization",headers)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals(5001,summary.single()["totalPay"].asInt())
        val direct=json.readTree(mvc.perform(get("/api/workers/${worker.id}/payroll?from=${java.time.YearMonth.of(year,month).atDay(1)}&to=${java.time.YearMonth.of(year,month).atEndOfMonth()}").header("Authorization",headers)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals(5001,direct["totalPay"].asInt())
        val hours=json.readTree(mvc.perform(get("/api/workers/${worker.id}/work-summary?from=${java.time.YearMonth.of(year,month).atDay(1)}&to=${java.time.YearMonth.of(year,month).atEndOfMonth()}").header("Authorization",headers)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals(30,hours["totalMinutes"].asInt())
        val dashboard=json.readTree(mvc.perform(get("/api/owner/dashboard/summary?date=${java.time.YearMonth.of(year,month).atDay(15)}").header("Authorization",headers)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals(5001,dashboard["month"]["accruedWage"].asInt())
    }

}
