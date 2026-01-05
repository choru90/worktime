package com.worktime.application.service

import com.worktime.adapter.output.persistence.StoreJpaRepository
import com.worktime.adapter.output.persistence.OwnerJpaRepository
import com.worktime.domain.exception.BusinessException
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.model.Owner
import com.worktime.domain.model.Store
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Store (영업장) 관리 서비스
 */
@Service
@Transactional(readOnly = true)
class StoreService(
    private val storeRepository: StoreJpaRepository,
    private val ownerRepository: OwnerJpaRepository
) {
    
    /**
     * 영업장 생성
     */
    @Transactional
    fun createStore(
        ownerId: UUID,
        name: String,
        address: String? = null,
        timezone: String = "Asia/Seoul"
    ): Store {
        val owner = ownerRepository.findById(ownerId)
            .orElseThrow { ResourceNotFoundException("Owner not found with id: $ownerId") }
        
        // 동일 이름 영업장 체크
        storeRepository.findByOwnerIdAndName(ownerId, name)?.let {
            throw BusinessException("이미 동일한 이름의 영업장이 존재합니다: $name")
        }
        
        val store = Store(
            owner = owner,
            name = name,
            address = address,
            timezone = timezone
        )
        
        return storeRepository.save(store)
    }
    
    /**
     * Owner의 모든 영업장 조회
     */
    fun getStoresByOwner(ownerId: UUID): List<Store> {
        return storeRepository.findByOwnerId(ownerId)
    }
    
    /**
     * Owner의 활성 영업장 조회
     */
    fun getActiveStoresByOwner(ownerId: UUID): List<Store> {
        return storeRepository.findByOwnerIdAndActiveTrue(ownerId)
    }
    
    /**
     * 영업장 조회
     */
    fun getStore(storeId: UUID): Store {
        return storeRepository.findById(storeId)
            .orElseThrow { ResourceNotFoundException("Store not found with id: $storeId") }
    }
    
    /**
     * 영업장 정보 수정
     */
    @Transactional
    fun updateStore(
        storeId: UUID,
        name: String? = null,
        address: String? = null,
        timezone: String? = null
    ): Store {
        val store = getStore(storeId)
        
        name?.let { 
            // 동일 이름 체크 (자기 자신 제외)
            storeRepository.findByOwnerIdAndName(store.owner.id, it)?.let { existing ->
                if (existing.id != storeId) {
                    throw BusinessException("이미 동일한 이름의 영업장이 존재합니다: $it")
                }
            }
            store.name = it 
        }
        address?.let { store.address = it }
        timezone?.let { store.timezone = it }
        
        return storeRepository.save(store)
    }
    
    /**
     * 영업장 비활성화
     */
    @Transactional
    fun deactivateStore(storeId: UUID): Store {
        val store = getStore(storeId)
        store.deactivate()
        return storeRepository.save(store)
    }
    
    /**
     * 영업장 활성화
     */
    @Transactional
    fun activateStore(storeId: UUID): Store {
        val store = getStore(storeId)
        store.activate()
        return storeRepository.save(store)
    }
}
