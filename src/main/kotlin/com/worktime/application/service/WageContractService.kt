package com.worktime.application.service

import com.worktime.adapter.input.web.dto.wage.WageContractCreateRequest
import com.worktime.adapter.input.web.dto.wage.WageContractResponse
import com.worktime.adapter.input.web.dto.wage.WageInfoResponse
import com.worktime.application.port.input.WageContractUseCase
import com.worktime.application.port.output.WageContractRepository
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.exception.BusinessException
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.model.WageContract
import com.worktime.domain.model.Worker
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.*

/**
 * Service implementing WageContract business logic.
 */
@Service
@Transactional
class WageContractService(
    private val wageContractRepository: WageContractRepository,
    private val workerRepository: WorkerRepository
) : WageContractUseCase {

    override fun createWageContract(workerId: UUID, request: WageContractCreateRequest): WageContractResponse {
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        
        // Validate non-overlapping periods
        validateNonOverlapping(worker, request.startDate, request.endDate)
        
        val contract = WageContract(
            worker = worker,
            hourlyWage = request.hourlyWage,
            startDate = request.startDate,
            endDate = request.endDate
        )
        
        val savedContract = wageContractRepository.save(contract)
        return WageContractResponse.from(savedContract)
    }

    override fun terminateWageContract(contractId: UUID, endDate: LocalDate): WageContractResponse {
        val contract = wageContractRepository.findById(contractId)
            ?: throw ResourceNotFoundException("Wage contract not found with id: $contractId")
        
        // 종료일이 시작일보다 이전일 수 없음
        if (endDate.isBefore(contract.startDate)) {
            throw BusinessException("End date cannot be before start date (${contract.startDate})")
        }
        
        // 이미 종료된 계약은 다시 종료할 수 없음
        if (contract.endDate != null) {
            throw BusinessException("Contract is already terminated on ${contract.endDate}")
        }
        
        contract.endDate = endDate
        val savedContract = wageContractRepository.save(contract)
        return WageContractResponse.from(savedContract)
    }

    override fun getWageOnDate(workerId: UUID, date: LocalDate): WageInfoResponse {
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        
        val contract = wageContractRepository.findActiveContractOnDate(worker, date)
        
        return WageInfoResponse(
            date = date,
            hourlyWage = contract?.hourlyWage,
            hasContract = contract != null
        )
    }

    override fun getCurrentWageContract(worker: Worker): WageContractResponse? {
        val contract = wageContractRepository.findCurrentActiveContract(worker)
        return contract?.let { WageContractResponse.from(it) }
    }

    override fun getWageHistory(worker: Worker): List<WageContractResponse> {
        return wageContractRepository.findByWorkerOrderByStartDateDesc(worker)
            .map { WageContractResponse.from(it) }
    }

    override fun getWageContractsByWorkerId(workerId: UUID): List<WageContractResponse> {
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        return getWageHistory(worker)
    }

    /**
     * Validate that the new contract period does not overlap with existing contracts.
     */
    private fun validateNonOverlapping(worker: Worker, startDate: LocalDate, endDate: LocalDate?) {
        val effectiveEndDate = endDate ?: LocalDate.of(2999, 12, 31)
        
        val overlappingContracts = wageContractRepository.findContractsOverlappingPeriod(
            worker = worker,
            startDate = startDate,
            endDate = effectiveEndDate
        )
        
        if (overlappingContracts.isNotEmpty()) {
            throw BusinessException(
                "Wage contract period overlaps with existing contract(s). " +
                "Please ensure the period does not conflict with: " +
                overlappingContracts.joinToString(", ") { 
                    "${it.startDate} ~ ${it.endDate ?: "ongoing"}" 
                }
            )
        }
    }
}
