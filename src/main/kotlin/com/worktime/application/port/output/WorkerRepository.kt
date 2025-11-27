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
    fun findByPhoneNumber(phoneNumber: String): Worker?
    fun existsByPhoneNumber(phoneNumber: String): Boolean
    fun delete(worker: Worker)
}
