package com.worktime.adapter.output.persistence

import com.worktime.domain.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

/**
 * User JPA Repository
 */
interface UserJpaRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}
