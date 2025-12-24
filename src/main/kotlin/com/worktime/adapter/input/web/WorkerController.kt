package com.worktime.adapter.input.web

import com.worktime.adapter.input.web.dto.worker.*
import com.worktime.application.port.input.WorkerUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * REST Controller for Worker operations.
 * OWNER 권한만 접근 가능
 */
@RestController
@RequestMapping("/api/workers")
@Tag(name = "근로자", description = "근로자 관리 API")
@PreAuthorize("hasRole('OWNER')")
class WorkerController(
    private val workerUseCase: WorkerUseCase
) {


    @PostMapping
    @Operation(
        summary = "새로운 근로자 생성",
        description = "제공된 정보로 새로운 근로자를 생성합니다"
    )
    fun createWorker(
        @Valid @RequestBody request: WorkerCreateRequest
    ): ResponseEntity<WorkerResponse> {
        val authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().authentication
        val userId = UUID.fromString(authentication.name)
        
        val response = workerUseCase.createWorker(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "근로자 정보 수정",
        description = "기존 근로자의 정보를 수정합니다"
    )
    fun updateWorker(
        @Parameter(description = "근로자 ID") @PathVariable id: UUID,
        @Valid @RequestBody request: WorkerUpdateRequest
    ): ResponseEntity<WorkerResponse> {
        val response = workerUseCase.updateWorker(id, request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "근로자 삭제 (비활성화)",
        description = "근로자를 소프트 삭제합니다 (active 상태를 false로 설정)"
    )
    fun deleteWorker(
        @Parameter(description = "근로자 ID") @PathVariable id: UUID
    ): ResponseEntity<Void> {
        workerUseCase.deleteWorker(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "근로자 상세 조회",
        description = "현재 시급 및 시급 이력을 포함한 근로자의 상세 정보를 조회합니다"
    )
    fun getWorkerDetail(
        @Parameter(description = "근로자 ID") @PathVariable id: UUID
    ): ResponseEntity<WorkerDetailResponse> {
        val response = workerUseCase.getWorkerDetail(id)
        return ResponseEntity.ok(response)
    }

    @GetMapping
    @Operation(
        summary = "전체 근로자 목록 조회",
        description = "전체 근로자 목록을 조회합니다. 활성 상태로 필터링할 수 있습니다."
    )
    fun getAllWorkers(
        @Parameter(description = "활성 상태로 필터링") @RequestParam(required = false) active: Boolean?
    ): ResponseEntity<List<WorkerResponse>> {
        val response = workerUseCase.getAllWorkers(active)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{id}/invite")
    @Operation(
        summary = "알바생 초대 코드 생성",
        description = "알바생 계정 연결을 위한 초대 코드를 생성합니다 (24시간 유효)"
    )
    fun generateInviteCode(
        @Parameter(description = "근로자 ID") @PathVariable id: UUID
    ): ResponseEntity<InviteCodeResponse> {
        val response = workerUseCase.generateInviteCode(id)
        return ResponseEntity.ok(response)
    }
}
