package com.worktime.application.service

import com.worktime.domain.model.Worker
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder

/** Invoked after the worker write lock/refresh, sharing order with resignation.
 * Inactive employees retain historical reads. Owners may still review history.
 */
internal fun enforceWorkerMutationPolicy(worker: Worker) {
    val employee = SecurityContextHolder.getContext().authentication?.authorities.orEmpty()
        .any { it.authority == "ROLE_WORKER" }
    if (employee && !worker.active) {
        throw AccessDeniedException("퇴사한 직원은 근무 기록과 일정을 변경할 수 없습니다")
    }
}
