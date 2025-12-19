package com.worktime.adapter.output.persistence

import com.worktime.application.port.output.OwnerRepository
import com.worktime.domain.model.Owner
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Owner Repository 구현체
 */
@Repository
class OwnerRepositoryAdapter(
    private val ownerJpaRepository: OwnerJpaRepository
) : OwnerRepository {
    
    override fun save(owner: Owner): Owner {
        return ownerJpaRepository.save(owner)
    }
    
    override fun findById(id: UUID): Owner? {
        return ownerJpaRepository.findById(id).orElse(null)
    }
    
    override fun findByUserId(userId: UUID): Owner? {
        return ownerJpaRepository.findByUserId(userId)
    }
}
