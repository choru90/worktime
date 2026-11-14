package com.worktime

import com.fasterxml.jackson.databind.ObjectMapper
import com.worktime.auth.security.JwtTokenProvider
import com.worktime.domain.model.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.LocalDate
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest(properties=["spring.datasource.url=jdbc:h2:mem:resignation;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","spring.jpa.show-sql=false","jwt.secret=synthetic-resignation-key-with-at-least-32-bytes","jwt.expiration=3600000"])
@AutoConfigureMockMvc
@Transactional
class PostResignationIntegrationTest {
    @Autowired lateinit var mvc:MockMvc
    @Autowired lateinit var em:EntityManager
    @Autowired lateinit var jwt:JwtTokenProvider
    @Autowired lateinit var json:ObjectMapper
    data class Fixture(val worker:Worker,val owner:String,val employee:String,val pending:Schedule,val confirmed:Schedule,val log:WorkLog)
    private val start=OffsetDateTime.parse("2025-06-15T09:00:00Z")
    private fun fixture():Fixture {
        fun user(role:UserRole)=User(email="${UUID.randomUUID()}@example.test",password="synthetic",name=role.name,role=role).also(em::persist)
        val account=user(UserRole.OWNER);val owner=Owner(user=account).also(em::persist);val employee=user(UserRole.WORKER)
        val worker=Worker(name="Former",phoneNumber="00000000000",owner=owner,userAccount=employee).also(em::persist)
        WageContract(worker=worker,hourlyWage=BigDecimal("10001"),startDate=LocalDate.of(2020,1,1)).also(em::persist)
        val pending=Schedule(worker=worker,startAtUtc=start.plusDays(2),endAtUtc=start.plusDays(2).plusHours(1),timezone="UTC",status=ScheduleStatus.PROPOSED_BY_OWNER).also(em::persist)
        val confirmed=Schedule(worker=worker,startAtUtc=start.plusDays(3),endAtUtc=start.plusDays(3).plusHours(1),timezone="UTC",status=ScheduleStatus.CONFIRMED).also(em::persist)
        val log=WorkLog(worker=worker,clockInAt=start,clockOutAt=start.plusMinutes(30),status=WorkLogStatus.COMPLETED).also(em::persist)
        em.flush()
        val f=Fixture(worker,"Bearer ${jwt.generateToken(account.id,account.role)}","Bearer ${jwt.generateToken(employee.id,employee.role)}",pending,confirmed,log)
        mvc.perform(post("/api/workers/${worker.id}/resign?resignDate=2025-06-30").header("Authorization",f.owner)).andExpect(status().isOk)
        em.flush();em.clear();return f
    }
    @ParameterizedTest
    @ValueSource(strings=["my-in","my-out","direct-in","direct-out","correction","create-schedule","update-schedule","cancel","worker-confirm","worker-reject","generic-confirm","generic-reject","manual"])
    fun `old employee JWT cannot write after resignation`(action:String) {
        val f=fixture()
        val at="""{"clockInAt":"$start","clockOutAt":"${start.plusHours(1)}"}"""
        val request=when(action) {
            "my-in"->post("/api/worklogs/my/clock-in")
            "my-out"->post("/api/worklogs/my/clock-out")
            "direct-in"->post("/api/workers/${f.worker.id}/clock-in").content("""{"clockInAt":"$start"}""")
            "direct-out"->post("/api/workers/${f.worker.id}/clock-out").content("""{"clockOutAt":"${start.plusHours(1)}"}""")
            "correction"->post("/api/worklogs/my/${f.log.id}/corrections").content("""{"clockInAt":"$start","clockOutAt":"${start.plusHours(1)}","reason":"edit","expectedStatus":"COMPLETED","expectedCorrectionCount":0}""")
            "create-schedule"->post("/api/workers/${f.worker.id}/schedules").content("""{"workerId":"${f.worker.id}","startAtUtc":"$start","endAtUtc":"${start.plusHours(1)}","timezone":"UTC","requestType":"WORKER_REQUEST"}""")
            "update-schedule"->patch("/api/schedules/${f.confirmed.id}").content("""{"startAtUtc":"$start","endAtUtc":"${start.plusHours(1)}","timezone":"UTC"}""")
            "cancel"->post("/api/schedules/${f.confirmed.id}/cancel")
            "worker-confirm"->post("/api/worker/schedules/${f.pending.id}/confirm")
            "worker-reject"->post("/api/worker/schedules/${f.pending.id}/reject")
            "generic-confirm"->post("/api/schedules/${f.pending.id}/confirm")
            "generic-reject"->post("/api/schedules/${f.pending.id}/reject")
            else->post("/api/worker/schedules/${f.confirmed.id}/worklog").content(at)
        }
        mvc.perform(request.header("Authorization",f.employee).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden)
        em.clear()
        val log=em.find(WorkLog::class.java,f.log.id)
        assertEquals(WorkLogStatus.COMPLETED,log.status);assertEquals(0,log.correctionCount)
        assertEquals(start.plusMinutes(30).toInstant(),log.clockOutAt!!.toInstant())
        assertEquals(ScheduleStatus.PROPOSED_BY_OWNER,em.find(Schedule::class.java,f.pending.id).status)
        assertEquals(ScheduleStatus.CONFIRMED,em.find(Schedule::class.java,f.confirmed.id).status)
        assertEquals(1L,em.createQuery("select count(w) from WorkLog w where w.worker.id=:id",java.lang.Long::class.java).setParameter("id",f.worker.id).singleResult)
    }
    @Test fun `old employee JWT keeps historical reads and owner can approve historical work`() {
        val f=fixture()
        mvc.perform(post("/api/work-logs/${f.log.id}/confirm").header("Authorization",f.owner)).andExpect(status().isOk)
        for(path in listOf("/api/workers/my","/api/work-logs/${f.log.id}","/api/worklogs/my?fromDate=2025-06-01&toDate=2025-06-30","/api/worker/schedules?from=2025-06-01T00:00:00Z&to=2025-07-01T00:00:00Z")) {
            mvc.perform(get(path).header("Authorization",f.employee)).andExpect(status().isOk)
        }
        val body=mvc.perform(get("/api/payroll/workers/${f.worker.id}/monthly?year=2025&month=6").header("Authorization",f.employee)).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(5001,json.readTree(body)["totalAmount"].asInt())
    }
}
