package com.worktime.adapter.input.web.dto.worker

import com.worktime.domain.model.Worker
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.*

/**
 * Response DTO for worker basic information.
 */
@Schema(description = "Worker basic information")
data class WorkerResponse(
    @Schema(description = "Worker ID")
    val id: UUID,
    
    @Schema(description = "Worker's name", example = "홍길동")
    val name: String,
    
    @Schema(description = "Worker's phone number", example = "01012345678")
    val phoneNumber: String,
    
    @Schema(description = "Active status")
    val active: Boolean,
    
    @Schema(description = "소속 영업장 ID")
    val storeId: UUID?,
    
    @Schema(description = "소속 영업장 이름")
    val storeName: String?,
    
    @Schema(description = "계정 연결 여부")
    val hasAccount: Boolean,
    
    @Schema(description = "연결된 계정 이메일")
    val userEmail: String?,
    
    @Schema(description = "Created timestamp")
    val createdAt: Instant,
    
    @Schema(description = "Last updated timestamp")
    val updatedAt: Instant
) {
    companion object {
        fun from(worker: Worker): WorkerResponse {
            return WorkerResponse(
                id = worker.id,
                name = worker.name,
                phoneNumber = worker.phoneNumber,
                active = worker.active,
                storeId = worker.store?.id,
                storeName = worker.store?.name,
                hasAccount = worker.userAccount != null,
                userEmail = worker.userAccount?.email,
                createdAt = worker.createdAt,
                updatedAt = worker.updatedAt
            )
        }
    }
}
