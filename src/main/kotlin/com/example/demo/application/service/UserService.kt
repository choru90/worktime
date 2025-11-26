package com.example.demo.application.service

import com.example.demo.application.port.input.UserUseCase
import com.example.demo.application.port.output.UserRepository
import com.example.demo.domain.model.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application service implementing the UserUseCase.
 * This orchestrates the business logic using the domain model and output ports.
 */
@Service
@Transactional
class UserService(
    private val userRepository: UserRepository
) : UserUseCase {

    override fun createUser(name: String, email: String): User {
        val user = User(name = name, email = email)
        return userRepository.save(user)
    }

    override fun getUserById(id: Long): User? {
        return userRepository.findById(id)
    }

    override fun getAllUsers(): List<User> {
        return userRepository.findAll()
    }
}
