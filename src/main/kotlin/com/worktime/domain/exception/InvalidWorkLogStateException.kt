package com.worktime.domain.exception

/**
 * ONGOING WorkLog가 없는데 clock-out 시도 시 발생하는 예외
 * HTTP 400 (Bad Request)
 */
class InvalidWorkLogStateException(
    message: String
) : BusinessException(message)
