package com.example.demo.adapter.output.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA Repository interface.
 */
@Repository
interface UserJpaRepository : JpaRepository<UserEntity, Long>
