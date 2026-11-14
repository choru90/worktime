package com.worktime.e2e

import com.worktime.domain.model.Worker
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

/** Only on the test classpath and the explicitly disposable e2eServer profile.
 * No wall-clock sleeping and no application-wide clock manipulation.
 */
@RestController
@Profile("wt002-e2e")
class InviteFixtureController(private val em: EntityManager) {
    @PostMapping("/api/e2e/fixtures/workers/{workerId}/expire-invite")
    @PreAuthorize("hasRole('OWNER') and @worktimeAccess.worker(#workerId)")
    @Transactional
    fun expire(@PathVariable workerId: UUID): Map<String, Boolean> {
        val worker = em.find(Worker::class.java, workerId)
        check(!worker.inviteCode.isNullOrBlank())
        worker.inviteCodeExpiresAt = OffsetDateTime.now().minusDays(1)
        return mapOf("expired" to true)
    }
}
