package com.worktime.adapter.input.web

import com.worktime.adapter.input.web.dto.schedule.ScheduleListResponse
import com.worktime.adapter.input.web.dto.store.RegularShiftCreateRequest
import com.worktime.adapter.input.web.dto.store.RegularShiftResponse
import com.worktime.adapter.input.web.dto.store.RegularShiftUpdateRequest
import com.worktime.application.service.RegularShiftService
import com.worktime.application.service.ScheduleService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.*

/**
 * RegularShift (고정 근무) 관리 API 컨트롤러
 */
@RestController
@RequestMapping("/api")
@Tag(name = "RegularShift", description = "고정 근무 관리 API")
@PreAuthorize("hasRole('OWNER')")
class RegularShiftController(
    private val regularShiftService: RegularShiftService,
    private val scheduleService: ScheduleService
) {
    
    @PostMapping("/stores/{storeId}/regular-shifts")
    @Operation(summary = "고정 근무 생성", description = "새 고정 근무를 등록합니다")
    fun createRegularShift(
        @PathVariable storeId: UUID,
        @Valid @RequestBody request: RegularShiftCreateRequest
    ): ResponseEntity<RegularShiftResponse> {
        val shift = regularShiftService.createRegularShift(
            storeId = storeId,
            workerId = request.workerId,
            dayOfWeek = request.dayOfWeek,
            startTime = request.startTime,
            endTime = request.endTime,
            shiftName = request.shiftName,
            effectiveFrom = request.effectiveFrom,
            effectiveUntil = request.effectiveUntil
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(RegularShiftResponse.from(shift))
    }
    
    @GetMapping("/stores/{storeId}/regular-shifts")
    @Operation(summary = "고정 근무 목록 조회", description = "영업장의 모든 고정 근무를 조회합니다")
    fun getRegularShiftsByStore(
        @PathVariable storeId: UUID,
        @RequestParam(required = false) activeOnly: Boolean = true
    ): ResponseEntity<List<RegularShiftResponse>> {
        val shifts = if (activeOnly) {
            regularShiftService.getActiveRegularShiftsByStore(storeId)
        } else {
            regularShiftService.getRegularShiftsByStore(storeId)
        }
        return ResponseEntity.ok(shifts.map { RegularShiftResponse.from(it) })
    }
    
    @GetMapping("/stores/{storeId}/regular-shifts/today")
    @Operation(summary = "오늘의 고정 근무 조회", description = "오늘 근무해야 할 고정 근무 목록을 조회합니다")
    fun getTodayShifts(
        @PathVariable storeId: UUID
    ): ResponseEntity<List<RegularShiftResponse>> {
        val shifts = regularShiftService.getTodayShifts(storeId)
        return ResponseEntity.ok(shifts.map { RegularShiftResponse.from(it) })
    }
    
    @GetMapping("/stores/{storeId}/regular-shifts/date/{date}")
    @Operation(summary = "특정 날짜 고정 근무 조회", description = "특정 날짜에 유효한 고정 근무를 조회합니다")
    fun getShiftsByDate(
        @PathVariable storeId: UUID,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<List<RegularShiftResponse>> {
        val shifts = regularShiftService.getEffectiveShiftsForDate(storeId, date)
        return ResponseEntity.ok(shifts.map { RegularShiftResponse.from(it) })
    }
    
    @GetMapping("/regular-shifts/{shiftId}")
    @Operation(summary = "고정 근무 상세 조회", description = "특정 고정 근무의 상세 정보를 조회합니다")
    fun getRegularShift(
        @PathVariable shiftId: UUID
    ): ResponseEntity<RegularShiftResponse> {
        val shift = regularShiftService.getRegularShift(shiftId)
        return ResponseEntity.ok(RegularShiftResponse.from(shift))
    }
    
    @PutMapping("/regular-shifts/{shiftId}")
    @Operation(summary = "고정 근무 수정", description = "고정 근무 정보를 수정합니다")
    fun updateRegularShift(
        @PathVariable shiftId: UUID,
        @Valid @RequestBody request: RegularShiftUpdateRequest
    ): ResponseEntity<RegularShiftResponse> {
        var shift = regularShiftService.getRegularShift(shiftId)
        
        // 담당자 변경
        request.workerId?.let {
            shift = regularShiftService.assignWorker(shiftId, it)
        }
        
        // 시간 변경
        if (request.startTime != null && request.endTime != null) {
            shift = regularShiftService.updateShiftTime(shiftId, request.startTime, request.endTime)
        }
        
        // 유효기간 변경
        if (request.effectiveFrom != null || request.effectiveUntil != null) {
            shift = regularShiftService.setEffectivePeriod(shiftId, request.effectiveFrom, request.effectiveUntil)
        }
        
        return ResponseEntity.ok(RegularShiftResponse.from(shift))
    }
    
    @DeleteMapping("/regular-shifts/{shiftId}")
    @Operation(summary = "고정 근무 비활성화", description = "고정 근무를 비활성화합니다")
    fun deactivateRegularShift(
        @PathVariable shiftId: UUID
    ): ResponseEntity<Void> {
        regularShiftService.deactivateShift(shiftId)
        return ResponseEntity.noContent().build()
    }
    
    @GetMapping("/workers/{workerId}/regular-shifts")
    @Operation(summary = "근로자의 고정 근무 조회", description = "특정 근로자의 고정 근무 목록을 조회합니다")
    fun getRegularShiftsByWorker(
        @PathVariable workerId: UUID
    ): ResponseEntity<List<RegularShiftResponse>> {
        val shifts = regularShiftService.getRegularShiftsByWorker(workerId)
        return ResponseEntity.ok(shifts.map { RegularShiftResponse.from(it) })
    }
    
    // ===== 월별 스케줄 생성 =====
    
    @PostMapping("/stores/{storeId}/generate-schedules")
    @Operation(summary = "월별 스케줄 자동 생성", description = "고정 근무를 기반으로 해당 월의 개별 스케줄을 생성합니다")
    fun generateMonthlySchedules(
        @PathVariable storeId: UUID,
        @RequestParam year: Int,
        @RequestParam month: Int
    ): ResponseEntity<ScheduleListResponse> {
        // 해당 영업장의 고정 근무 조회
        val regularShifts = regularShiftService.getActiveRegularShiftsByStore(storeId)
        
        // 월별 스케줄 생성
        val schedules = scheduleService.generateMonthlySchedules(storeId, year, month, regularShifts)
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ScheduleListResponse.from(schedules))
    }
}
