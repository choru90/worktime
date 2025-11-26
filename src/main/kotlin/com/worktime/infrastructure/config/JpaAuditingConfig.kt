package com.worktime.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/**
 * Configuration for JPA Auditing.
 * Enables automatic population of @CreatedDate and @LastModifiedDate fields.
 */
@Configuration
@EnableJpaAuditing
class JpaAuditingConfig
