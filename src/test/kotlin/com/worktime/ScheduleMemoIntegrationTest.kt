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
import java.util.UUID

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:schedule_memo;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false", "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=WARN",
    "jwt.secret=synthetic-memo-test-key-with-at-least-32-bytes", "jwt.expiration=3600000"
])
@AutoConfigureMockMvc
@Transactional
class ScheduleMemoIntegrationTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var em: EntityManager
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var jwt: JwtTokenProvider
    private fun fixture(): Triple<Worker, String, String> {
        val ownerUser=User(email="owner-${UUID.randomUUID()}@example.test",password="synthetic",name="Owner",role=UserRole.OWNER).also(em::persist)
        val owner=Owner(user=ownerUser).also(em::persist)
        val employee=User(email="employee-${UUID.randomUUID()}@example.test",password="synthetic",name="Employee",role=UserRole.WORKER).also(em::persist)
        val worker=Worker(name="Synthetic memo worker",phoneNumber="00000000000",owner=owner,userAccount=employee).also(em::persist)
        em.flush()
        return Triple(worker,"Bearer "+jwt.generateToken(ownerUser.id,ownerUser.role),"Bearer "+jwt.generateToken(employee.id,employee.role))
    }
    private fun create(worker: Worker, token: String, extra: String) = mvc.perform(post("/api/workers/${worker.id}/schedules")
        .header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
        .content("""{"workerId":"${worker.id}","startAtUtc":"2026-11-27T20:00:00+09:00","endAtUtc":"2026-11-27T21:00:00+09:00","timezone":"Asia/Seoul","requestType":"OWNER_PROPOSAL"$extra}"""))
    @Test fun `memo survives database reload lists and employee acceptance`() {
        val (worker,owner,employee)=fixture()
        val memo="마감 인수인계\n<안전한 텍스트> & 준비"
        val created=mapper.readTree(create(worker,owner,",\"memo\":"+mapper.writeValueAsString(memo)).andExpect(status().isCreated).andReturn().response.contentAsByteArray)
        assertEquals(memo,created.path("memo").textValue())
        val id=created.path("id").asText();em.flush();em.clear()
        for (token in listOf(owner,employee)) {
            val detail=mapper.readTree(mvc.perform(get("/api/schedules/$id").header("Authorization",token)).andExpect(status().isOk).andReturn().response.contentAsByteArray)
            assertEquals(memo,detail.path("memo").textValue())
        }
        for ((path,token) in listOf("/api/schedules" to owner,"/api/worker/schedules" to employee)) {
            val list=mapper.readTree(mvc.perform(get(path).param("from","2026-11-27T00:00:00Z").param("to","2026-11-28T00:00:00Z").header("Authorization",token)).andExpect(status().isOk).andReturn().response.contentAsByteArray)
            assertEquals(memo,list.path("schedules").first().path("memo").textValue())
        }
        val accepted=mapper.readTree(mvc.perform(post("/api/worker/schedules/$id/confirm").header("Authorization",employee)).andExpect(status().isOk).andReturn().response.contentAsByteArray)
        assertEquals("CONFIRMED",accepted.path("status").asText());assertEquals(memo,accepted.path("memo").textValue())
        em.flush();em.clear()
        assertEquals(memo,mapper.readTree(mvc.perform(get("/api/schedules/$id").header("Authorization",owner)).andReturn().response.contentAsByteArray).path("memo").textValue())
    }
    @Test fun `omitted null and empty memo retain compatible proposal behavior`() {
        for(extra in listOf("",",\"memo\":null",",\"memo\":\"\"")) {
            val (worker,owner,employee)=fixture()
            val created=mapper.readTree(create(worker,owner,extra).andExpect(status().isCreated).andReturn().response.contentAsByteArray)
            assertEquals("PROPOSED_BY_OWNER",created.path("status").asText())
            em.flush();em.clear()
            mvc.perform(post("/api/worker/schedules/${created.path("id").asText()}/confirm").header("Authorization",employee)).andExpect(status().isOk)
        }
    }
    @Test fun `memo accepts 500 characters and rejects larger text before storing`() {
        val (worker,owner,_)=fixture()
        create(worker,owner,",\"memo\":"+mapper.writeValueAsString("가".repeat(500))).andExpect(status().isCreated)
        create(worker,owner,",\"memo\":"+mapper.writeValueAsString("가".repeat(501))).andExpect(status().isBadRequest)
        assertEquals(1L,em.createQuery("select count(s) from Schedule s where s.worker.id=:id",java.lang.Long::class.java).setParameter("id",worker.id).singleResult.toLong())
    }
}
