package com.worktime.application.port.input

import com.worktime.adapter.input.web.dto.worker.*
import java.util.*

/**
 * Input port (Use Case) for Worker operations.
 */
interface WorkerUseCase {
    fun createWorker(userId: UUID, request: WorkerCreateRequest): WorkerResponse
    fun updateWorker(id: UUID, request: WorkerUpdateRequest): WorkerResponse
    fun deleteWorker(id: UUID)
    fun getWorkerById(id: UUID): WorkerResponse
    fun getWorkerDetail(id: UUID): WorkerDetailResponse
    fun getAllWorkers(active: Boolean?): List<WorkerResponse>
    fun generateInviteCode(workerId: UUID): InviteCodeResponse
}
