package com.worktime.application.port.output

import com.worktime.domain.model.User
import java.util.*

/**
 * User Repository Output Port
 */
interface UserRepository {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun save(user: User): User
    fun findById(id: UUID): User?
}
