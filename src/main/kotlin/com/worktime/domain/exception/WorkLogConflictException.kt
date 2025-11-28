package com.worktime.domain.exception

/**
 * 이미 ONGOING WorkLog가 있는데 clock-in 시도 시 발생하는 예외
 * HTTP 409 (Conflict)
 */
class WorkLogConflictException(
    message: String
) : BusinessException(message)
