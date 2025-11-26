package com.worktime.infrastructure.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Swagger/OpenAPI 문서화 설정
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
                    .description("알바 관리 서비스 - Spring Boot, Kotlin, Hexagonal Architecture로 구축된 Worker, Schedule, Work Log 관리 API")
            )
    }
}
