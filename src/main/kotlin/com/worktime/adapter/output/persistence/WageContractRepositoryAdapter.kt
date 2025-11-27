package com.worktime.adapter.output.persistence

import com.worktime.application.port.output.WageContractRepository
import com.worktime.domain.model.WageContract
import com.worktime.domain.model.Worker
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

/**
 * Adapter implementing the WageContractRepository port using JPA.
 */
@Repository
class WageContractRepositoryAdapter(
    private val jpaRepository: WageContractJpaRepository
) : WageContractRepository {

    override fun save(contract: WageContract): WageContract {
        return jpaRepository.save(contract)
    }

    override fun findById(id: UUID): WageContract? {
        return jpaRepository.findById(id).orElse(null)
    }

    override fun findByWorker(worker: Worker): List<WageContract> {
        return jpaRepository.findByWorker(worker)
    }

    override fun findByWorkerOrderByStartDateDesc(worker: Worker): List<WageContract> {
        return jpaRepository.findByWorkerOrderByStartDateDesc(worker)
    }

    override fun findCurrentActiveContract(worker: Worker): WageContract? {
        return jpaRepository.findByWorkerAndEndDateIsNull(worker)
    }

    override fun findActiveContractOnDate(worker: Worker, date: LocalDate): WageContract? {
        return jpaRepository.findActiveContractOnDate(worker, date)
    }

    override fun findContractsOverlappingPeriod(
        worker: Worker,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<WageContract> {
        return jpaRepository.findContractsOverlappingPeriod(worker, startDate, endDate)
    }
}
