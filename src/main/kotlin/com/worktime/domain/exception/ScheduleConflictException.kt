package com.worktime.domain.exception

/**
 * 스케줄 시간이 중복되는 경우 발생하는 예외
 * HTTP 409 (Conflict)
 */
class ScheduleConflictException(
    message: String
) : BusinessException(message)
