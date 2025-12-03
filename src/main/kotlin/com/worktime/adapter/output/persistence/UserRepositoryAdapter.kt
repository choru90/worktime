package com.worktime.adapter.output.persistence

import com.worktime.application.port.output.UserRepository
import com.worktime.domain.model.User
import org.springframework.stereotype.Repository
import java.util.*

/**
 * User Repository Adapter
 * UserRepository Output Port의 구현체
 */
@Repository
class UserRepositoryAdapter(
    private val userJpaRepository: UserJpaRepository
) : UserRepository {
    
    override fun findByEmail(email: String): User? {
        return userJpaRepository.findByEmail(email)
    }
    
    override fun existsByEmail(email: String): Boolean {
        return userJpaRepository.existsByEmail(email)
    }
    
    override fun save(user: User): User {
        return userJpaRepository.save(user)
    }
    
    override fun findById(id: UUID): User? {
        return userJpaRepository.findById(id).orElse(null)
    }
}
