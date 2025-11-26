package com.worktime.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/**
 * JPA Auditing 설정
 * @CreatedDate 및 @LastModifiedDate 필드의 자동 설정을 활성화합니다.
 */
@Configuration
@EnableJpaAuditing
class JpaAuditingConfig
