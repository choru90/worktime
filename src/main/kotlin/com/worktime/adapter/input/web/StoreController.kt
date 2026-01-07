package com.worktime.adapter.input.web

import com.worktime.adapter.input.web.dto.store.BusinessHoursListRequest
import com.worktime.adapter.input.web.dto.store.BusinessHoursResponse
import com.worktime.adapter.input.web.dto.store.StoreCreateRequest
import com.worktime.adapter.input.web.dto.store.StoreResponse
import com.worktime.adapter.input.web.dto.store.StoreUpdateRequest
import com.worktime.adapter.output.persistence.OwnerJpaRepository
import com.worktime.application.service.BusinessHoursRequest
import com.worktime.application.service.BusinessHoursService
import com.worktime.application.service.StoreService
import com.worktime.domain.exception.ResourceNotFoundException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Store (영업장) 관리 API 컨트롤러
 */
@RestController
@RequestMapping("/api/stores")
@Tag(name = "Store", description = "영업장 관리 API")
@PreAuthorize("hasRole('OWNER')")
class StoreController(
    private val storeService: StoreService,
    private val businessHoursService: BusinessHoursService,
    private val ownerRepository: OwnerJpaRepository
) {
    
    private fun getCurrentOwnerId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
        val userId = UUID.fromString(authentication.name)
        val owner = ownerRepository.findByUserId(userId)
            ?: throw ResourceNotFoundException("Owner not found for user id: $userId")
        return owner.id
    }
    
    @PostMapping
    @Operation(summary = "영업장 생성", description = "새 영업장을 등록합니다")
    fun createStore(
        @Valid @RequestBody request: StoreCreateRequest
    ): ResponseEntity<StoreResponse> {
        val ownerId = getCurrentOwnerId()
        val store = storeService.createStore(
            ownerId = ownerId,
            name = request.name,
            address = request.address,
            timezone = request.timezone
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(StoreResponse.from(store))
    }
    
    @GetMapping
    @Operation(summary = "내 영업장 목록 조회", description = "로그인한 사장의 모든 영업장을 조회합니다")
    fun getMyStores(): ResponseEntity<List<StoreResponse>> {
        val ownerId = getCurrentOwnerId()
        val stores = storeService.getActiveStoresByOwner(ownerId)
        return ResponseEntity.ok(stores.map { StoreResponse.from(it) })
    }
    
    @GetMapping("/{storeId}")
    @Operation(summary = "영업장 상세 조회", description = "특정 영업장의 상세 정보를 조회합니다")
    fun getStore(
        @PathVariable storeId: UUID
    ): ResponseEntity<StoreResponse> {
        val store = storeService.getStore(storeId)
        return ResponseEntity.ok(StoreResponse.from(store))
    }
    
    @PutMapping("/{storeId}")
    @Operation(summary = "영업장 수정", description = "영업장 정보를 수정합니다")
    fun updateStore(
        @PathVariable storeId: UUID,
        @Valid @RequestBody request: StoreUpdateRequest
    ): ResponseEntity<StoreResponse> {
        val store = storeService.updateStore(
            storeId = storeId,
            name = request.name,
            address = request.address,
            timezone = request.timezone
        )
        return ResponseEntity.ok(StoreResponse.from(store))
    }
    
    @DeleteMapping("/{storeId}")
    @Operation(summary = "영업장 비활성화", description = "영업장을 비활성화합니다")
    fun deactivateStore(
        @PathVariable storeId: UUID
    ): ResponseEntity<Void> {
        storeService.deactivateStore(storeId)
        return ResponseEntity.noContent().build()
    }
    
    // ===== 영업시간 관리 =====
    
    @GetMapping("/{storeId}/business-hours")
    @Operation(summary = "영업시간 조회", description = "영업장의 영업시간을 조회합니다")
    fun getBusinessHours(
        @PathVariable storeId: UUID
    ): ResponseEntity<List<BusinessHoursResponse>> {
        val hours = businessHoursService.getBusinessHours(storeId)
        return ResponseEntity.ok(hours.map { BusinessHoursResponse.from(it) })
    }
    
    @PutMapping("/{storeId}/business-hours")
    @Operation(summary = "영업시간 설정", description = "영업장의 영업시간을 설정합니다 (7일 전체)")
    fun setBusinessHours(
        @PathVariable storeId: UUID,
        @Valid @RequestBody request: BusinessHoursListRequest
    ): ResponseEntity<List<BusinessHoursResponse>> {
        val hoursRequests = request.hours.map { 
            BusinessHoursRequest(
                dayOfWeek = it.dayOfWeek,
                openTime = it.openTime,
                closeTime = it.closeTime,
                isClosed = it.isClosed
            )
        }
        val hours = businessHoursService.setBusinessHours(storeId, hoursRequests)
        return ResponseEntity.ok(hours.map { BusinessHoursResponse.from(it) })
    }
}
