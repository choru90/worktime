package com.worktime

import com.worktime.application.service.PayrollService
import com.worktime.domain.exception.BusinessException
import com.worktime.domain.exception.ResourceNotFoundException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * WT-001 P0: PayrollService rejects invalid input before querying data.
 * Isolated H2 context; no production credentials or shared accounts.
 */
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:wt001_payroll_validation;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
    "jwt.secret=wt001-local-synthetic-test-key-with-at-least-32-bytes",
    "jwt.expiration=3600000"
])
@Transactional
class PayrollValidationIntegrationTest {
    @Autowired lateinit var payroll: PayrollService

    private val unknownId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @ParameterizedTest
    @CsvSource("1899,6", "2101,6", "2025,0", "2025,13")
    fun `monthly payroll rejects out of range year or month`(year: Int, month: Int) {
        assertThrows(BusinessException::class.java) {
            payroll.getMonthlyPayrollSummary(unknownId, year, month, null)
        }
    }

    @ParameterizedTest
    @CsvSource("1899,6", "2101,6", "2025,0", "2025,13")
    fun `worker payroll detail rejects out of range year or month`(year: Int, month: Int) {
        assertThrows(BusinessException::class.java) {
            payroll.getWorkerMonthlyPayrollDetail(unknownId, year, month)
        }
    }

    @Test
    fun `payroll rejects reversed date range`() {
        assertThrows(BusinessException::class.java) {
            payroll.calculatePayroll(unknownId, LocalDate.of(2025, 7, 1), LocalDate.of(2025, 6, 30))
        }
    }

    @Test
    fun `work summary rejects reversed date range`() {
        assertThrows(BusinessException::class.java) {
            payroll.getWorkSummary(unknownId, LocalDate.of(2025, 7, 1), LocalDate.of(2025, 6, 30))
        }
    }

    @Test
    fun `valid monthly payroll with unknown owner fails closed`() {
        assertThrows(ResourceNotFoundException::class.java) {
            payroll.getMonthlyPayrollSummary(unknownId, 2025, 6, null)
        }
    }

    @Test
    fun `valid worker payroll with unknown worker fails closed`() {
        assertThrows(ResourceNotFoundException::class.java) {
            payroll.getWorkerMonthlyPayrollDetail(unknownId, 2025, 6)
        }
    }

    @Test
    fun `valid direct payroll with unknown worker fails closed`() {
        assertThrows(ResourceNotFoundException::class.java) {
            payroll.calculatePayroll(unknownId, LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30))
        }
    }

    @Test
    fun `valid work summary with unknown worker fails closed`() {
        assertThrows(ResourceNotFoundException::class.java) {
            payroll.getWorkSummary(unknownId, LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30))
        }
    }
}
