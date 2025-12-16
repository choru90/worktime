package com.worktime.adapter.input.web.dto.dashboard

enum class AttendanceExceptionType {
    SCHEDULE_EXISTS_BUT_NO_CHECKIN,    // 스케줄은 있는데 체크인이 없음 (결근/지각/누락)
    CHECKIN_EXISTS_BUT_NO_CHECKOUT,    // 체크인은 있는데 체크아웃이 없음 (퇴근 미체크)
    LOG_EXISTS_BUT_NO_SCHEDULE         // 스케줄 없이 근무 기록 발생 (추후 구현)
}
