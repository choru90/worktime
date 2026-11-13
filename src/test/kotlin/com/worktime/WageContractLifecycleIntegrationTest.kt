package com.worktime

import com.worktime.adapter.input.web.dto.wage.WageContractCreateRequest
import com.worktime.application.service.WageContractService
import com.worktime.domain.exception.BusinessException
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.model.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:wt001_wage_lifecycle;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
    "jwt.secret=wt001-local-synthetic-test-key-with-at-least-32-bytes",
    "jwt.expiration=3600000"
])
@Transactional
class WageContractLifecycleIntegrationTest {
    @Autowired lateinit var service: WageContractService
    @Autowired lateinit var em: EntityManager

    private fun worker(): Worker {
        val user = User(email = "${UUID.randomUUID()}@example.test", password = "synthetic",
            name = "Owner", role = UserRole.OWNER).also(em::persist)
        val owner = Owner(user = user).also(em::persist)
        return Worker(name = "Synthetic", phoneNumber = "00000000000", owner = owner).also(em::persist)
    }

    @Test
    fun `wage is absent when worker has no contract`() {
        val w = worker()
        val info = service.getWageOnDate(w.id, LocalDate.of(2025, 6, 1))
        assertFalse(info.hasContract)
        assertNull(info.hourlyWage)
        assertNull(service.getCurrentWageContract(w))
        assertTrue(service.getWageHistory(w).isEmpty())
    }

    @Test
    fun `contract date boundaries and history are inclusive`() {
        val w = worker()
        val start = LocalDate.of(2025, 6, 1)
        val end = LocalDate.of(2025, 6, 30)
        service.createWageContract(w.id, WageContractCreateRequest(BigDecimal("10000"), start, end))
        assertFalse(service.getWageOnDate(w.id, start.minusDays(1)).hasContract)
        assertTrue(service.getWageOnDate(w.id, start).hasContract)
        assertTrue(service.getWageOnDate(w.id, end).hasContract)
        assertFalse(service.getWageOnDate(w.id, end.plusDays(1)).hasContract)
        assertEquals(1, service.getWageContractsByWorkerId(w.id).size)
    }

    @Test
    fun `creating invalid date range does not persist a contract`() {
        val w = worker()
        assertThrows(BusinessException::class.java) {
            service.createWageContract(w.id, WageContractCreateRequest(
                BigDecimal("10000"), LocalDate.of(2025, 7, 1), LocalDate.of(2025, 6, 30)))
        }
        assertTrue(service.getWageHistory(w).isEmpty())
    }

    @Test
    fun `active contract is automatically closed when newer contract starts`() {
        val w = worker()
        val start = LocalDate.of(2025, 1, 1)
        service.createWageContract(w.id, WageContractCreateRequest(BigDecimal("10000"), start))
        service.createWageContract(w.id, WageContractCreateRequest(BigDecimal("12000"), LocalDate.of(2025, 7, 1)))
        assertEquals(BigDecimal("10000"), service.getWageOnDate(w.id, LocalDate.of(2025, 6, 30)).hourlyWage)
        assertEquals(BigDecimal("12000"), service.getWageOnDate(w.id, LocalDate.of(2025, 7, 1)).hourlyWage)
        assertEquals(2, service.getWageHistory(w).size)
    }

    @Test
    fun `overlapping historical contract is rejected`() {
        val w = worker()
        service.createWageContract(w.id, WageContractCreateRequest(
            BigDecimal("10000"), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30)))
        assertThrows(BusinessException::class.java) {
            service.createWageContract(w.id, WageContractCreateRequest(
                BigDecimal("12000"), LocalDate.of(2025, 6, 1), LocalDate.of(2025, 7, 31)))
        }
    }

    @Test
    fun `termination before start leaves contract unchanged`() {
        val w = worker()
        val created = service.createWageContract(w.id, WageContractCreateRequest(
            BigDecimal("10000"), LocalDate.of(2025, 7, 1)))
        val result = service.terminateWageContract(created.id, LocalDate.of(2025, 6, 30))
        assertNull(result.endDate)
        assertTrue(service.getWageOnDate(w.id, LocalDate.of(2025, 7, 1)).hasContract)
    }

    @Test
    fun `terminated contract cannot be terminated twice`() {
        val w = worker()
        val created = service.createWageContract(w.id, WageContractCreateRequest(
            BigDecimal("10000"), LocalDate.of(2025, 1, 1)))
        service.terminateWageContract(created.id, LocalDate.of(2025, 6, 30))
        assertThrows(BusinessException::class.java) {
            service.terminateWageContract(created.id, LocalDate.of(2025, 7, 31))
        }
        assertFalse(service.getWageOnDate(w.id, LocalDate.of(2025, 7, 1)).hasContract)
    }

    @Test
    fun `unknown worker and contract fail closed`() {
        val missing = UUID.fromString("00000000-0000-0000-0000-000000000001")
        assertThrows(ResourceNotFoundException::class.java) {
            service.getWageOnDate(missing, LocalDate.of(2025, 6, 1))
        }
        assertThrows(ResourceNotFoundException::class.java) {
            service.getWageContractsByWorkerId(missing)
        }
        assertThrows(ResourceNotFoundException::class.java) {
            service.terminateWageContract(missing, LocalDate.of(2025, 6, 1))
        }
        assertThrows(ResourceNotFoundException::class.java) {
            service.createWageContract(missing, WageContractCreateRequest(
                BigDecimal("10000"), LocalDate.of(2025, 6, 1)))
        }
    }
}
