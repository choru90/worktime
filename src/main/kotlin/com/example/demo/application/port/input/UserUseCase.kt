package com.example.demo.application.port.input

import com.example.demo.domain.model.User

/**
 * Input port (Use Case interface) for User operations.
 * This defines the contract that will be implemented by the application service.
 */
interface UserUseCase {
    fun createUser(name: String, email: String): User
    fun getUserById(id: Long): User?
    fun getAllUsers(): List<User>
}
