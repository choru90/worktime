package com.worktime.adapter.output.persistence

import com.worktime.domain.model.Worker
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * JPA Repository for Worker entity.
 */
@Repository
interface WorkerJpaRepository : JpaRepository<Worker, UUID> {
    
    /**
     * Find active workers
     */
    fun findByActiveTrue(): List<Worker>

    /**
     * Find workers by active status
     */
    fun findByActive(active: Boolean): List<Worker>

    /**
     * Find workers by owner ID
     */
    fun findByOwnerId(ownerId: UUID): List<Worker>
    
    /**
     * Find worker by phone number
     */
    fun findByPhoneNumber(phoneNumber: String): Worker?
    
    /**
     * Check if a worker exists with the given phone number
     */
    fun existsByPhoneNumber(phoneNumber: String): Boolean
    
    /**
     * Find worker by user account ID
     */
    fun findByUserAccountId(userId: UUID): Worker?
}
