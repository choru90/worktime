package com.worktime.application.port.input

import com.worktime.adapter.input.web.dto.wage.WageContractCreateRequest
import com.worktime.adapter.input.web.dto.wage.WageContractResponse
import com.worktime.adapter.input.web.dto.wage.WageInfoResponse
import com.worktime.domain.model.Worker
import java.time.LocalDate
import java.util.*

/**
 * Input port (Use Case) for WageContract operations.
 */
interface WageContractUseCase {
    fun createWageContract(workerId: UUID, request: WageContractCreateRequest): WageContractResponse
    fun getWageOnDate(workerId: UUID, date: LocalDate): WageInfoResponse
    fun getCurrentWageContract(worker: Worker): WageContractResponse?
    fun getWageHistory(worker: Worker): List<WageContractResponse>
}
