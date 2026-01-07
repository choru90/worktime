package com.worktime.application.service

import com.worktime.adapter.input.web.dto.worker.*
import com.worktime.application.port.input.WageContractUseCase
import com.worktime.application.port.input.WorkerUseCase
import com.worktime.application.port.output.OwnerRepository
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
    private val ownerRepository: OwnerRepository
) : WorkerUseCase {

    override fun createWorker(userId: UUID, request: WorkerCreateRequest): WorkerResponse {
        val owner = ownerRepository.findByUserId(userId)
            ?: throw ResourceNotFoundException("Owner not found for user id: $userId")

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

    override fun resignWorker(id: UUID, resignDate: java.time.LocalDate): WorkerResponse {
        val worker = workerRepository.findById(id)
            ?: throw ResourceNotFoundException("Worker not found with id: $id")
        
        // 현재 활성 시급 계약 종료
        val currentWageContract = wageContractUseCase.getCurrentWageContract(worker)
        if (currentWageContract != null) {
            wageContractUseCase.terminateWageContract(currentWageContract.id, resignDate)
        }
        
        // Worker 상태를 INACTIVE로 변경
        worker.active = false
        workerRepository.save(worker)
        
        return WorkerResponse.from(worker)
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
    
    override fun generateInviteCode(workerId: UUID): InviteCodeResponse {
        val worker = workerRepository.findById(workerId)
            ?: throw ResourceNotFoundException("Worker not found with id: $workerId")
        
        // 이미 계정이 연결된 경우 에러
        if (worker.userAccount != null) {
            throw com.worktime.domain.exception.BusinessException("이미 계정이 연결된 알바생입니다.")
        }
        
        val code = worker.generateInviteCode()
        val savedWorker = workerRepository.save(worker)
        
        return InviteCodeResponse(
            inviteCode = code,
            expiresAt = savedWorker.inviteCodeExpiresAt!!,
            workerName = savedWorker.name
        )
    }
}
