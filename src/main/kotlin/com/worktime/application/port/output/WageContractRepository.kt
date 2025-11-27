package com.worktime.application.port.output

import com.worktime.domain.model.WageContract
import com.worktime.domain.model.Worker
import java.time.LocalDate
import java.util.*

/**
 * Output port (Repository interface) for WageContract persistence.
 */
interface WageContractRepository {
    fun save(contract: WageContract): WageContract
    fun findById(id: UUID): WageContract?
    fun findByWorker(worker: Worker): List<WageContract>
    fun findByWorkerOrderByStartDateDesc(worker: Worker): List<WageContract>
    fun findCurrentActiveContract(worker: Worker): WageContract?
    fun findActiveContractOnDate(worker: Worker, date: LocalDate): WageContract?
    fun findContractsOverlappingPeriod(worker: Worker, startDate: LocalDate, endDate: LocalDate): List<WageContract>
}
