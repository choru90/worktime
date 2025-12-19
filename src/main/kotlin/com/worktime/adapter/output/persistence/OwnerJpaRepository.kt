package com.worktime.adapter.output.persistence

import com.worktime.domain.model.Owner
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

/**
 * Owner JPA Repository
 */
interface OwnerJpaRepository : JpaRepository<Owner, UUID> {
    fun findByUserId(userId: UUID): Owner?
}
