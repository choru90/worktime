package com.worktime.application.port.output

import com.worktime.domain.model.User

/**
 * Output port (Repository interface) for User persistence.
 * This is defined in the application layer and implemented by the adapter layer.
 */
interface UserRepository {
    fun save(user: User): User
    fun findById(id: Long): User?
    fun findAll(): List<User>
}
