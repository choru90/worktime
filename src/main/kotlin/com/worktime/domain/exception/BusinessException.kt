package com.worktime.domain.exception

/**
 * Base exception for business logic violations.
 */
open class BusinessException(
    message: String
) : RuntimeException(message)
