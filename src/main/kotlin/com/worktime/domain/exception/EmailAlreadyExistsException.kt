package com.worktime.domain.exception

/**
 * 이미 사용 중인 이메일로 회원가입 시도 시 발생하는 예외
 */
class EmailAlreadyExistsException(
    email: String
) : BusinessException("이미 사용 중인 이메일입니다: $email")
