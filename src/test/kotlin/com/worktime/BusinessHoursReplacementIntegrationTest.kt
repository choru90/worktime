package com.worktime

import com.worktime.application.service.BusinessHoursRequest
import com.worktime.application.service.BusinessHoursService
import com.worktime.domain.model.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

@SpringBootTest(properties=["spring.datasource.url=jdbc:h2:mem:business_hours_rule;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","spring.jpa.show-sql=false","jwt.secret=synthetic-business-hours-rule-test-key-with-at-least-32-bytes"])
@Transactional
class BusinessHoursReplacementIntegrationTest {
    @Autowired lateinit var service:BusinessHoursService
    @Autowired lateinit var em:EntityManager
    private fun store():Store {
        val user=User(email="${UUID.randomUUID()}@example.test",password="synthetic",name="Owner",role=UserRole.OWNER).also(em::persist)
        val owner=Owner(user=user).also(em::persist)
        return Store(owner=owner,name="Cafe").also(em::persist)
    }
    @ParameterizedTest
    @CsvSource("false,09:00,18:00", "false,09:00,09:00", "false,18:00,09:00", "true,09:00,18:00", "true,09:00,09:00", "true,18:00,09:00")
    fun `open and closed day boundaries persist or reject without deleting previous hours`(closed:Boolean,start:String,end:String) {
        val store=store()
        service.setBusinessHours(store.id,listOf(BusinessHoursRequest(DayOfWeek.MONDAY,LocalTime.of(8,0),LocalTime.of(17,0))))
        em.flush();em.clear()
        val request=BusinessHoursRequest(DayOfWeek.TUESDAY,LocalTime.parse(start),LocalTime.parse(end),closed)
        if(!closed && !request.openTime.isBefore(request.closeTime)) {
            assertThrows<IllegalArgumentException> { service.setBusinessHours(store.id,listOf(request)) }
            em.clear()
            val original=service.getBusinessHours(store.id).single()
            assertEquals(DayOfWeek.MONDAY,original.dayOfWeek);assertEquals(LocalTime.of(8,0),original.openTime);assertFalse(original.isClosed)
        } else {
            service.setBusinessHours(store.id,listOf(request));em.flush();em.clear()
            val saved=service.getBusinessHours(store.id).single()
            assertEquals(DayOfWeek.TUESDAY,saved.dayOfWeek);assertEquals(closed,saved.isClosed)
            assertEquals(request.openTime,saved.openTime);assertEquals(request.closeTime,saved.closeTime)
        }
    }
    @Test fun `empty replacement deletes hours explicitly`() {
        val store=store()
        service.setBusinessHours(store.id,listOf(BusinessHoursRequest(DayOfWeek.MONDAY,LocalTime.of(9,0),LocalTime.of(18,0))))
        em.flush();em.clear();assertTrue(service.setBusinessHours(store.id,emptyList()).isEmpty())
        em.flush();em.clear();assertTrue(service.getBusinessHours(store.id).isEmpty())
    }
    @Test fun `same weekday replacement removes old identity before inserting new hours`() {
        val store=store()
        service.setBusinessHours(store.id,listOf(BusinessHoursRequest(DayOfWeek.MONDAY,LocalTime.of(9,0),LocalTime.of(18,0))))
        em.flush();em.clear()
        service.setBusinessHours(store.id,listOf(BusinessHoursRequest(DayOfWeek.MONDAY,LocalTime.of(10,0),LocalTime.of(19,0))))
        em.flush();em.clear()
        val saved=service.getBusinessHours(store.id).single()
        assertEquals(LocalTime.of(10,0),saved.openTime);assertEquals(LocalTime.of(19,0),saved.closeTime)
    }

}
