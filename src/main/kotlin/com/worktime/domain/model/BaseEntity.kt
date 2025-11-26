package com.worktime.domain.model

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * 공통 감사 필드가 있는 기본 엔티티
 * 모든 도메인 엔티티는 이 클래스를 상속해야 합니다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set
    
    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set
}
