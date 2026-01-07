package com.worktime.adapter.input.web.dto.store

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * 고정 근무 생성 요청
 */
data class RegularShiftCreateRequest(
    val workerId: UUID,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val shiftName: String? = null,
    val effectiveFrom: LocalDate? = null,
    val effectiveUntil: LocalDate? = null
)

/**
 * 고정 근무 수정 요청
 */
data class RegularShiftUpdateRequest(
    val workerId: UUID? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val shiftName: String? = null,
    val effectiveFrom: LocalDate? = null,
    val effectiveUntil: LocalDate? = null
)

/**
 * 고정 근무 응답
 */
data class RegularShiftResponse(
    val id: UUID,
    val storeId: UUID,
    val workerId: UUID,
    val workerName: String,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val shiftName: String?,
    val active: Boolean,
    val effectiveFrom: LocalDate?,
    val effectiveUntil: LocalDate?
) {
    companion object {
        fun from(shift: com.worktime.domain.model.RegularShift): RegularShiftResponse {
            return RegularShiftResponse(
                id = shift.id,
                storeId = shift.store.id,
                workerId = shift.worker.id,
                workerName = shift.worker.name,
                dayOfWeek = shift.dayOfWeek,
                startTime = shift.startTime,
                endTime = shift.endTime,
                shiftName = shift.shiftName,
                active = shift.active,
                effectiveFrom = shift.effectiveFrom,
                effectiveUntil = shift.effectiveUntil
            )
        }
    }
}
