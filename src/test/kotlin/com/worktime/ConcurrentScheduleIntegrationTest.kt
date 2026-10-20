package com.worktime

import com.worktime.application.service.ScheduleService
import com.worktime.domain.exception.InvalidScheduleStateException
import com.worktime.domain.exception.ScheduleConflictException
import com.worktime.domain.model.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.*

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:wtschedule;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false", "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=WARN",
    "jwt.secret=synthetic-schedule-test-key-with-at-least-32-bytes", "jwt.expiration=3600000"
])
class ConcurrentScheduleIntegrationTest {
    companion object {
        @JvmStatic @DynamicPropertySource
        fun mysql(registry: DynamicPropertyRegistry) {
            val url = System.getenv("WORKTIME_SCHEDULE_TEST_URL") ?: return
            require(url == "jdbc:mysql://127.0.0.1:3306/worktime_schedule_test?serverTimezone=UTC") {
                "Only the isolated loopback worktime_schedule_test schema is allowed"
            }
            registry.add("spring.datasource.url") { url }
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
            registry.add("spring.datasource.username") { "worktime" }
            registry.add("spring.datasource.password") { "synthetic-ci-password" }
            registry.add("spring.jpa.database-platform") { "org.hibernate.dialect.MySQLDialect" }
        }
    }
    @Autowired lateinit var em: EntityManager
    @Autowired lateinit var transaction: TransactionTemplate
    @Autowired lateinit var service: ScheduleService
    private val start = OffsetDateTime.parse("2026-11-01T09:00:00Z")
    private fun fixture(ranges: List<Pair<Long, Long>>, statuses: List<ScheduleStatus> = ranges.map { ScheduleStatus.PROPOSED_BY_OWNER }): List<UUID> = transaction.execute {
        val user = User(email="schedule-${UUID.randomUUID()}@example.invalid",password="synthetic",name="Owner",role=UserRole.OWNER)
        em.persist(user);val owner=Owner(user=user);em.persist(owner)
        val worker=Worker(name="Synthetic",phoneNumber="00000000000",owner=owner);em.persist(worker)
        ranges.mapIndexed { i, range ->
            val schedule=Schedule(worker=worker,startAtUtc=start.plusHours(range.first),endAtUtc=start.plusHours(range.second),timezone="Asia/Seoul",status=statuses[i])
            em.persist(schedule);schedule.id
        }
    }!!
    private fun statuses(ids: List<UUID>): List<ScheduleStatus> = transaction.execute {
        ids.map { em.find(Schedule::class.java,it).status }
    }!!
    private fun race(ids: List<UUID>): List<String> {
        val pool=Executors.newFixedThreadPool(2);val gate=CountDownLatch(1)
        try {
            val jobs=ids.map { id -> pool.submit(Callable {
                check(gate.await(5,TimeUnit.SECONDS))
                try { service.confirmSchedule(id);"confirmed" }
                catch (_: ScheduleConflictException) { "conflict" }
                catch (_: InvalidScheduleStateException) { "invalid" }
            }) }
            gate.countDown();return jobs.map { it.get(20,TimeUnit.SECONDS) }.sorted()
        } finally { pool.shutdownNow() }
    }
    @Test fun `simultaneous overlapping proposals confirm only one`() {
        val ids=fixture(listOf(0L to 4L,2L to 6L))
        assertEquals(listOf("confirmed","conflict"),race(ids))
        assertEquals(1,statuses(ids).count { it==ScheduleStatus.CONFIRMED })
    }
    @Test fun `same proposal cannot confirm twice from an old persistence snapshot`() {
        val id=fixture(listOf(0L to 4L)).single()
        assertEquals(listOf("confirmed","invalid"),race(listOf(id,id)))
    }
    @Test fun `already confirmed overlap rejects pending proposal without changing it`() {
        val ids=fixture(listOf(0L to 4L,2L to 6L),listOf(ScheduleStatus.CONFIRMED,ScheduleStatus.PROPOSED_BY_OWNER))
        assertThrows(ScheduleConflictException::class.java) { service.confirmSchedule(ids[1]) }
        assertEquals(ScheduleStatus.PROPOSED_BY_OWNER,statuses(ids)[1])
    }
    @Test fun `adjacent shifts may both confirm`() {
        val ids=fixture(listOf(0L to 4L,4L to 8L))
        ids.forEach { service.confirmSchedule(it) }
        assertEquals(listOf(ScheduleStatus.CONFIRMED,ScheduleStatus.CONFIRMED),statuses(ids))
    }
    @Test fun `containing multiday confirmed schedule is not missed by overlap query`() {
        val ids=fixture(listOf(-72L to 72L,0L to 1L),listOf(ScheduleStatus.CONFIRMED,ScheduleStatus.PROPOSED_BY_OWNER))
        assertThrows(ScheduleConflictException::class.java) { service.confirmSchedule(ids[1]) }
    }
    @Test fun `updating confirmed shift into another confirmed shift is rejected`() {
        val ids=fixture(listOf(0L to 4L,6L to 8L),listOf(ScheduleStatus.CONFIRMED,ScheduleStatus.CONFIRMED))
        assertThrows(ScheduleConflictException::class.java) {
            service.updateScheduleTime(ids[1],com.worktime.adapter.input.web.dto.schedule.ScheduleUpdateRequest(start.plusHours(2),start.plusHours(7),"Asia/Seoul"))
        }
        val actual=transaction.execute { em.find(Schedule::class.java,ids[1]).startAtUtc }!!
        assertEquals(start.plusHours(6).toInstant(),actual.toInstant())
    }
}
