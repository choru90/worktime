package com.worktime.adapter.input.web.dto.worker

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * Request DTO for updating an existing worker.
 */
@Schema(description = "Request to update a worker")
data class WorkerUpdateRequest(
    @field:NotBlank(message = "Name is required")
    @Schema(description = "Worker's name", example = "홍길동", required = true)
    val name: String,
    
    @field:NotBlank(message = "Phone number is required")
    @field:Pattern(regexp = "^\\d{10,11}$", message = "Phone number must be 10-11 digits")
    @Schema(description = "Worker's phone number", example = "01012345678", required = true)
    val phoneNumber: String
)
