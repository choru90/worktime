package com.worktime.domain.exception

/**
 * 스케줄 상태 전이가 불가능한 경우 발생하는 예외
 * HTTP 400 (Bad Request)
 */
class InvalidScheduleStateException(
    message: String
) : BusinessException(message)
