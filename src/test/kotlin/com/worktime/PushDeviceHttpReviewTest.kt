package com.worktime

import com.worktime.adapter.output.push.PushQueue
import com.worktime.auth.security.JwtTokenProvider
import com.worktime.domain.model.UserRole
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:pushreview;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false", "worktime.push.enabled=false",
    "jwt.secret=review-only-synthetic-signing-key-at-least-32-bytes", "jwt.expiration=3600000"
])
@AutoConfigureMockMvc
class PushDeviceHttpReviewTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var jwt: JwtTokenProvider
    @MockBean lateinit var queue: PushQueue
    private val user = UUID.randomUUID()
    private val token = "ExpoPushToken[synthetic-http-review]"
    private fun auth() = "Bearer ${jwt.generateToken(user, UserRole.WORKER)}"
    @Test fun `anonymous requests cannot register or revoke devices`() {
        mvc.perform(put("/api/notifications/push-device").contentType(MediaType.APPLICATION_JSON).content("""{"token":"$token"}""")).andExpect(status().isUnauthorized)
        mvc.perform(delete("/api/notifications/push-device").contentType(MediaType.APPLICATION_JSON).content("""{"token":"$token"}""")).andExpect(status().isUnauthorized)
        verify(queue, never()).register(any(), any())
        verify(queue, never()).unregister(any(), any())
    }
    @Test fun `registration uses JWT principal and ignores request user ID`() {
        whenever(queue.enabled).thenReturn(true)
        mvc.perform(put("/api/notifications/push-device").header("Authorization",auth()).contentType(MediaType.APPLICATION_JSON).content("""{"token":"$token","userId":"${UUID.randomUUID()}"}""")).andExpect(status().isNoContent)
        verify(queue).register(user,token)
    }
    @Test fun `revocation is scoped to JWT principal`() {
        mvc.perform(delete("/api/notifications/push-device").header("Authorization",auth()).contentType(MediaType.APPLICATION_JSON).content("""{"token":"$token"}""")).andExpect(status().isNoContent)
        verify(queue).unregister(user,token)
    }
    @Test fun `malformed token is rejected before persistence`() {
        whenever(queue.enabled).thenReturn(true)
        mvc.perform(put("/api/notifications/push-device").header("Authorization",auth()).contentType(MediaType.APPLICATION_JSON).content("""{"token":"invalid-token"}""")).andExpect(status().isBadRequest)
        verify(queue,never()).register(any(),any())
    }
    @Test fun `disabled backend rejects opt in but still allows opt out`() {
        whenever(queue.enabled).thenReturn(false)
        mvc.perform(put("/api/notifications/push-device").header("Authorization",auth()).contentType(MediaType.APPLICATION_JSON).content("""{"token":"$token"}""")).andExpect(status().isServiceUnavailable)
        mvc.perform(delete("/api/notifications/push-device").header("Authorization",auth()).contentType(MediaType.APPLICATION_JSON).content("""{"token":"$token"}""")).andExpect(status().isNoContent)
    }
}
