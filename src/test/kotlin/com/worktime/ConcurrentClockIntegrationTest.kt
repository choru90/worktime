package com.worktime

import com.worktime.auth.security.JwtTokenProvider
import com.worktime.domain.model.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:wtconcurrent;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
    "jwt.secret=wt001-local-synthetic-test-key-with-at-least-32-bytes", "jwt.expiration=3600000"
])
@AutoConfigureMockMvc
class ConcurrentClockIntegrationTest {
    companion object {
        /** Optional, isolated MySQL acceptance check. Never point create-drop at a shared DB. */
        @JvmStatic
        @org.springframework.test.context.DynamicPropertySource
        fun isolatedMysql(registry: org.springframework.test.context.DynamicPropertyRegistry) {
            val url = System.getenv("WORKTIME_MYSQL_TEST_URL") ?: return
            require(url.startsWith("jdbc:mysql://127.0.0.1:13307/worktime_merge_test?")) {
                "Only the disposable local worktime_merge_test schema is allowed"
            }
            registry.add("spring.datasource.url") { url }
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
            registry.add("spring.datasource.username") { "root" }
            registry.add("spring.datasource.password") { "" }
            registry.add("spring.jpa.database-platform") { "org.hibernate.dialect.MySQLDialect" }
        }
    }

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var em: EntityManager
    @Autowired lateinit var transaction: TransactionTemplate
    @Autowired lateinit var jwt: JwtTokenProvider

    @Test
    fun `two simultaneous clock ins produce one record`() {
        lateinit var employee: User
        lateinit var worker: Worker
        transaction.executeWithoutResult {
            val ownerUser = User(email="concurrent-owner@example.test", password="synthetic", name="Owner", role=UserRole.OWNER)
            em.persist(ownerUser)
            val owner = Owner(user=ownerUser); em.persist(owner)
            employee = User(email="concurrent-worker@example.test", password="synthetic", name="Worker", role=UserRole.WORKER)
            em.persist(employee)
            worker = Worker(name="Concurrent", phoneNumber="00000000000", owner=owner, userAccount=employee)
            em.persist(worker)
        }
        val token = "Bearer ${jwt.generateToken(employee.id, employee.role)}"
        val pool = Executors.newFixedThreadPool(2)
        val gate = CountDownLatch(1)
        try {
            val calls = (1..2).map { pool.submit(Callable {
                gate.await(5, TimeUnit.SECONDS)
                mvc.perform(post("/api/worklogs/my/clock-in").header("Authorization", token)).andReturn().response.status
            }) }
            gate.countDown()
            assertEquals(listOf(201, 409), calls.map { it.get(15, TimeUnit.SECONDS) }.sorted())
            val count = transaction.execute {
                em.createQuery("select count(w) from WorkLog w where w.worker.id = :id", java.lang.Long::class.java)
                    .setParameter("id", worker.id).singleResult.toLong()
            }
            assertEquals(1L, count)
        } finally { pool.shutdownNow() }
    }
}
