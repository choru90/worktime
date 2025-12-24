package com.worktime.adapter.output.persistence

import com.worktime.application.port.output.WorkerRepository
import com.worktime.domain.model.Worker
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Adapter implementing the WorkerRepository port using JPA.
 */
@Repository
class WorkerRepositoryAdapter(
    private val jpaRepository: WorkerJpaRepository
) : WorkerRepository {

    override fun save(worker: Worker): Worker {
        return jpaRepository.save(worker)
    }

    override fun findById(id: UUID): Worker? {
        return jpaRepository.findById(id).orElse(null)
    }

    override fun findAll(): List<Worker> {
        return jpaRepository.findAll()
    }

    override fun findByActive(active: Boolean): List<Worker> {
        return jpaRepository.findByActive(active)
    }

    override fun findByOwner(ownerId: UUID): List<Worker> {
        return jpaRepository.findByOwnerId(ownerId)
    }

    override fun findByPhoneNumber(phoneNumber: String): Worker? {
        return jpaRepository.findByPhoneNumber(phoneNumber)
    }

    override fun existsByPhoneNumber(phoneNumber: String): Boolean {
        return jpaRepository.existsByPhoneNumber(phoneNumber)
    }

    override fun findByUserAccountId(userId: UUID): Worker? {
        return jpaRepository.findByUserAccountId(userId)
    }

    override fun delete(worker: Worker) {
        jpaRepository.delete(worker)
    }
}
