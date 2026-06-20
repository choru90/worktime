package com.worktime.application.service

import com.worktime.adapter.input.web.dto.worker.*
import com.worktime.adapter.output.persistence.StoreJpaRepository
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
    private val ownerRepository: OwnerRepository,
    private val storeRepository: StoreJpaRepository
) : WorkerUseCase {

    override fun createWorker(userId: UUID, request: WorkerCreateRequest): WorkerResponse {
        val owner = ownerRepository.findByUserId(userId)
            ?: throw ResourceNotFoundException("Owner not found for user id: $userId")

        val store = request.storeId?.let { storeId ->
            storeRepository.findById(storeId)
                .orElseThrow { ResourceNotFoundException("Store not found with id: $storeId") }
        }

        val worker = Worker(
            name = request.name,
            phoneNumber = request.phoneNumber,
            owner = owner,
            store = store
        )
        
        val savedWorker = workerRepository.save(worker)
        return WorkerResponse.from(savedWorker)
    }

    override fun updateWorker(id: UUID, request: WorkerUpdateRequest): WorkerResponse {
        val worker = workerRepository.findById(id)
            ?: throw ResourceNotFoundException("Worker not found with id: $id")
        
        worker.name = request.name
        worker.phoneNumber = request.phoneNumber
        worker.store = request.storeId?.let { storeId ->
            storeRepository.findById(storeId)
                .orElseThrow { ResourceNotFoundException("Store not found with id: $storeId") }
        }
        
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

    override fun getAllWorkers(userId: UUID, active: Boolean?, storeId: UUID?): List<WorkerResponse> {
        val owner = ownerRepository.findByUserId(userId)
            ?: throw ResourceNotFoundException("Owner not found for user id: $userId")

        val workerList = when {
            storeId != null -> workerRepository.findByStoreId(storeId)
            else -> workerRepository.findByOwner(owner.id)
        }
        
        val filteredWorkerList = when {
            storeId != null && active != null -> workerList.filter { it.active == active && it.owner.id == owner.id }
            storeId != null -> workerList.filter { it.owner.id == owner.id }
            active != null -> workerList.filter { it.active == active }
            else -> workerList
        }
        
        return filteredWorkerList.map { WorkerResponse.from(it) }
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
