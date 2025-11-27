package com.worktime.adapter.input.web

import com.worktime.adapter.input.web.dto.worker.*
import com.worktime.application.port.input.WorkerUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * REST Controller for Worker operations.
 */
@RestController
@RequestMapping("/api/workers")
@Tag(name = "Worker", description = "Worker management API")
class WorkerController(
    private val workerUseCase: WorkerUseCase
) {

    @PostMapping
    @Operation(
        summary = "Create a new worker",
        description = "Creates a new worker with the provided information"
    )
    fun createWorker(
        @Valid @RequestBody request: WorkerCreateRequest
    ): ResponseEntity<WorkerResponse> {
        val response = workerUseCase.createWorker(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update a worker",
        description = "Updates an existing worker's information"
    )
    fun updateWorker(
        @Parameter(description = "Worker ID") @PathVariable id: UUID,
        @Valid @RequestBody request: WorkerUpdateRequest
    ): ResponseEntity<WorkerResponse> {
        val response = workerUseCase.updateWorker(id, request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete (deactivate) a worker",
        description = "Soft deletes a worker by setting active status to false"
    )
    fun deleteWorker(
        @Parameter(description = "Worker ID") @PathVariable id: UUID
    ): ResponseEntity<Void> {
        workerUseCase.deleteWorker(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get worker by ID",
        description = "Retrieves detailed worker information including current wage and wage history"
    )
    fun getWorkerDetail(
        @Parameter(description = "Worker ID") @PathVariable id: UUID
    ): ResponseEntity<WorkerDetailResponse> {
        val response = workerUseCase.getWorkerDetail(id)
        return ResponseEntity.ok(response)
    }

    @GetMapping
    @Operation(
        summary = "Get all workers",
        description = "Retrieves all workers, optionally filtered by active status"
    )
    fun getAllWorkers(
        @Parameter(description = "Filter by active status") @RequestParam(required = false) active: Boolean?
    ): ResponseEntity<List<WorkerResponse>> {
        val response = workerUseCase.getAllWorkers(active)
        return ResponseEntity.ok(response)
    }
}
