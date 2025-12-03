package com.worktime.domain.exception

/**
 * 인증 실패 예외
 */
class InvalidCredentialsException(
    message: String
) : RuntimeException(message)
