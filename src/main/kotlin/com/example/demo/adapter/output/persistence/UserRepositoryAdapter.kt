package com.example.demo.adapter.output.persistence

import com.example.demo.application.port.output.UserRepository
import com.example.demo.domain.model.User
import org.springframework.stereotype.Repository

/**
 * Adapter implementing the UserRepository port (Outbound Adapter).
 * This converts between domain models and JPA entities.
 */
@Repository
class UserRepositoryAdapter(
    private val jpaRepository: UserJpaRepository
) : UserRepository {

    override fun save(user: User): User {
        val entity = user.toEntity()
        val savedEntity = jpaRepository.save(entity)
        return savedEntity.toDomain()
    }

    override fun findById(id: Long): User? {
        return jpaRepository.findById(id)
            .map { it.toDomain() }
            .orElse(null)
    }

    override fun findAll(): List<User> {
        return jpaRepository.findAll().map { it.toDomain() }
    }

    // Mapper functions
    private fun User.toEntity(): UserEntity {
        return UserEntity(
            id = this.id,
            name = this.name,
            email = this.email
        )
    }

    private fun UserEntity.toDomain(): User {
        return User(
            id = this.id,
            name = this.name,
            email = this.email
        )
    }
}
