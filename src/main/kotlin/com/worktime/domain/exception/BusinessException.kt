package com.worktime.domain.exception

/**
 * Exception thrown when a business rule is violated.
 * E.g., wage contract period overlap, invalid state transitions, etc.
 */
class BusinessException(
    message: String
) : RuntimeException(message)
