package com.worktime.application.service

import com.worktime.application.port.output.*
import com.worktime.adapter.input.web.dto.schedule.ScheduleCreateRequest
import com.worktime.adapter.input.web.dto.schedule.ScheduleRequestType
import com.worktime.domain.exception.ResourceNotFoundException
import com.worktime.domain.model.ScheduleStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Shared HTTP boundary ownership checks. Foreign identifiers are indistinguishable from missing ones. */
@Service("worktimeAccess")
@Transactional(readOnly = true)
class WorktimeAccess(
    private val workers: WorkerRepository,
    private val owners: OwnerRepository,
    private val logs: WorkLogRepository,
    private val schedules: ScheduleRepository
) {
    private fun authentication() = SecurityContextHolder.getContext().authentication
        ?: throw AccessDeniedException("Authentication required")
    private fun userId() = UUID.fromString(authentication().name)
    private fun isOwner() = authentication().authorities.any { it.authority == "ROLE_OWNER" }
    private fun isWorker() = authentication().authorities.any { it.authority == "ROLE_WORKER" }
    private fun missing(): Nothing = throw ResourceNotFoundException("Resource not found")

    fun ownerId(): UUID {
        if (!isOwner()) throw AccessDeniedException("Owner role required")
        return owners.findByUserId(userId())?.id ?: missing()
    }

    fun worker(id: UUID): Boolean {
        val target = workers.findById(id) ?: missing()
        val permitted = when {
            isOwner() -> target.owner.user.id == userId()
            isWorker() -> target.userAccount?.id == userId()
            else -> false
        }
        if (!permitted) missing()
        return true
    }

    fun log(id: UUID): Boolean = worker((logs.findById(id) ?: missing()).worker.id)

    fun schedule(id: UUID): Boolean = worker((schedules.findById(id) ?: missing()).worker.id)

    fun respondSchedule(id: UUID): Boolean {
        val target = schedules.findById(id) ?: missing()
        worker(target.worker.id)
        val expected = if (isOwner()) ScheduleStatus.REQUESTED_BY_WORKER else ScheduleStatus.PROPOSED_BY_OWNER
        if (target.status != expected) throw AccessDeniedException("Cannot respond to this schedule")
        return true
    }

    fun createSchedule(id: UUID, request: ScheduleCreateRequest): Boolean {
        worker(id)
        if (request.workerId != id) missing()
        val expected = if (isOwner()) ScheduleRequestType.OWNER_PROPOSAL else ScheduleRequestType.WORKER_REQUEST
        if (request.requestType != expected) throw AccessDeniedException("Invalid request role")
        return true
    }
}
