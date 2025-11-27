package com.worktime.adapter.input.web.dto

import java.time.Instant

/**
 * Standard error response structure.
 */
data class ErrorResponse(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String? = null
)
