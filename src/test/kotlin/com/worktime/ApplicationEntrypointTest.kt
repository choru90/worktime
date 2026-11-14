package com.worktime

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Receives the real application context so the entrypoint smoke test always closes it. */
class EntrypointContextListener : ApplicationListener<ContextRefreshedEvent> {
    override fun onApplicationEvent(event: ContextRefreshedEvent) {
        context=event.applicationContext as ConfigurableApplicationContext
    }
    companion object { var context: ConfigurableApplicationContext?=null }
}

class ApplicationEntrypointTest {
    @Test fun `real main starts web server with isolated schema and exposes healthy actuator`() {
        EntrypointContextListener.context=null
        try {
            main(arrayOf(
                "--server.port=0",
                "--context.listener.classes=com.worktime.EntrypointContextListener",
                "--spring.datasource.url=jdbc:h2:mem:wt001_entrypoint;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver","--spring.datasource.username=sa","--spring.datasource.password=",
                "--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.flyway.enabled=false","--spring.jpa.show-sql=false","--logging.level.org.hibernate.SQL=WARN",
                "--logging.level.root=WARN","--jwt.secret=synthetic-entrypoint-test-key-with-at-least-32-bytes"
            ))
            val context=requireNotNull(EntrypointContextListener.context)
            assertTrue(context.isActive)
            val port=requireNotNull(context.environment.getProperty("local.server.port"))
            val request=HttpRequest.newBuilder(URI("http://127.0.0.1:$port/actuator/health")).timeout(Duration.ofSeconds(10)).GET().build()
            val response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString())
            assertEquals(200,response.statusCode());assertTrue(response.body().contains("\"status\":\"UP\""))
        } finally {
            EntrypointContextListener.context?.close()
            EntrypointContextListener.context=null
        }
    }
}
