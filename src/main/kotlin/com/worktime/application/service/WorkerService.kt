package com.worktime.application.service

import com.worktime.adapter.input.web.dto.worker.*
import com.worktime.application.port.input.WageContractUseCase
import com.worktime.application.port.input.WorkerUseCase
import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.model.Worker
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service implementing Worker business logic.
 */
@Service
@Transactional
class WorkerService(
    private val workerRepository: WorkerRepository,
    private val wageContractUseCase: WageContractUseCase,
    private val userRepository: com.worktime.application.port.output.UserRepository
) : WorkerUseCase {

    override fun createWorker(ownerId: UUID, request: WorkerCreateRequest): WorkerResponse {
        val owner = userRepository.findById(ownerId)
            ?: throw ResourceNotFoundException("Owner not found with id: $ownerId")

        val worker = Worker(
            name = request.name,
            phoneNumber = request.phoneNumber,
            owner = owner
        )
        
        val savedWorker = workerRepository.save(worker)
        return WorkerResponse.from(savedWorker)
    }

    override fun updateWorker(id: UUID, request: WorkerUpdateRequest): WorkerResponse {
        val worker = workerRepository.findById(id)
            ?: throw ResourceNotFoundException("Worker not found with id: $id")
        
        worker.name = request.name
        worker.phoneNumber = request.phoneNumber
        
        val updatedWorker = workerRepository.save(worker)
        return WorkerResponse.from(updatedWorker)
    }

    override fun deleteWorker(id: UUID) {
        val worker = workerRepository.findById(id)
            ?: throw ResourceNotFoundException("Worker not found with id: $id")
        
        // Soft delete by setting active to false
        worker.active = false
        workerRepository.save(worker)
    }

    override fun getWorkerById(id: UUID): WorkerResponse {
        val worker = workerRepository.findById(id)
            ?: throw ResourceNotFoundException("Worker not found with id: $id")
        
        return WorkerResponse.from(worker)
    }

    override fun getWorkerDetail(id: UUID): WorkerDetailResponse {
        val worker = workerRepository.findById(id)
            ?: throw ResourceNotFoundException("Worker not found with id: $id")
        
        val currentWage = wageContractUseCase.getCurrentWageContract(worker)
        val wageHistory = wageContractUseCase.getWageHistory(worker)
        
        return WorkerDetailResponse(
            worker = WorkerResponse.from(worker),
            currentWage = currentWage,
            wageHistory = wageHistory
        )
    }

    override fun getAllWorkers(active: Boolean?): List<WorkerResponse> {
        val workers = if (active != null) {
            workerRepository.findByActive(active)
        } else {
            workerRepository.findAll()
        }
        
        return workers.map { WorkerResponse.from(it) }
    }
}
