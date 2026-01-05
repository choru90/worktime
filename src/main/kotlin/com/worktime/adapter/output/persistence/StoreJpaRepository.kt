package com.worktime.adapter.output.persistence

import com.worktime.domain.model.Store
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Store JPA Repository
 */
@Repository
interface StoreJpaRepository : JpaRepository<Store, UUID> {
    
    /**
     * Owner의 모든 영업장 조회
     */
    fun findByOwnerId(ownerId: UUID): List<Store>
    
    /**
     * Owner의 활성 영업장 조회
     */
    fun findByOwnerIdAndActiveTrue(ownerId: UUID): List<Store>
    
    /**
     * 영업장 이름으로 조회
     */
    fun findByOwnerIdAndName(ownerId: UUID, name: String): Store?
}
