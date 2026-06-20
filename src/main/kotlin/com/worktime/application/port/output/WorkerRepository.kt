package com.worktime.application.port.output

import com.worktime.domain.model.Worker
import java.util.*

/**
 * Output port (Repository interface) for Worker persistence.
 */
interface WorkerRepository {
    fun save(worker: Worker): Worker
    fun findById(id: UUID): Worker?
    fun findAll(): List<Worker>
    fun findByActive(active: Boolean): List<Worker>
    fun findByOwner(ownerId: UUID): List<Worker>
    fun findByStoreId(storeId: UUID): List<Worker>
    fun findByPhoneNumber(phoneNumber: String): Worker?
    fun existsByPhoneNumber(phoneNumber: String): Boolean
    fun findByUserAccountId(userId: UUID): Worker?
    fun findByInviteCode(inviteCode: String): Worker?
    fun delete(worker: Worker)
}
