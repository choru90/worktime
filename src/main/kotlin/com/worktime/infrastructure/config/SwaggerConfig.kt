package com.worktime.infrastructure.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for Swagger/OpenAPI documentation.
 */
@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Worktime API")
                    .version("1.0.0")
                    .description("알바 관리 서비스 - Worker, Schedule, and Work Log Management API built with Spring Boot, Kotlin, and Hexagonal Architecture")
            )
    }
}
