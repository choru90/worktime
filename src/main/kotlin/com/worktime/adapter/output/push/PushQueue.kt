package com.worktime.adapter.output.push

import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.nio.ByteBuffer
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Component
class PushQueue(
    private val jdbc: JdbcTemplate,
    @Value("\${worktime.push.enabled:false}") val enabled: Boolean
) {
    @Transactional
    fun register(userId: UUID, token: String) {
        jdbc.update(
            """INSERT INTO push_devices (push_token, user_id, binding_id) VALUES (?, ?, ?)
               ON DUPLICATE KEY UPDATE binding_id = IF(user_id = ?, binding_id, ?), user_id = ?""",
            token, userId.bytes(), UUID.randomUUID().bytes(),
            userId.bytes(), UUID.randomUUID().bytes(), userId.bytes()
        )
    }

    fun unregister(userId: UUID, token: String) {
        jdbc.update("DELETE FROM push_devices WHERE user_id = ? AND push_token = ?", userId.bytes(), token)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun enqueue(userId: UUID) {
        if (!enabled) return
        jdbc.update(
            """INSERT INTO push_deliveries (push_token, binding_id, expires_at)
               SELECT push_token, binding_id, ? FROM push_devices WHERE user_id = ?""",
            Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS)), userId.bytes()
        )
    }

    data class Delivery(val id: Long, val token: String, val binding: ByteArray, val attempts: Int, val ticket: String?)

    fun due(now: Timestamp): List<Delivery> {
        jdbc.update("DELETE FROM push_deliveries WHERE expires_at <= ?", now)
        return jdbc.query(
            """SELECT id, push_token, binding_id, attempts, ticket_id FROM push_deliveries
               WHERE due_at <= ? ORDER BY due_at, id LIMIT 30""",
            { row, _ -> Delivery(row.getLong(1), row.getString(2), row.getBytes(3), row.getInt(4), row.getString(5)) },
            now
        )
    }

    fun claim(job: Delivery, now: Timestamp): Boolean = jdbc.update(
        "UPDATE push_deliveries SET due_at = ?, attempts = attempts + 1 WHERE id = ? AND due_at <= ? AND attempts = ?",
        Timestamp.from(now.toInstant().plusSeconds(120)), job.id, now, job.attempts
    ) == 1

    fun isRegistered(job: Delivery): Boolean = jdbc.queryForObject(
        "SELECT COUNT(*) FROM push_devices WHERE push_token = ? AND binding_id = ?",
        Int::class.java, job.token, job.binding
    ) == 1

    fun discardDevice(job: Delivery) {
        jdbc.update("DELETE FROM push_devices WHERE push_token = ? AND binding_id = ?", job.token, job.binding)
    }

    fun finish(job: Delivery) {
        jdbc.update("DELETE FROM push_deliveries WHERE id = ?", job.id)
    }

    fun reschedule(job: Delivery, delaySeconds: Long, ticket: String? = job.ticket) {
        jdbc.update(
            "UPDATE push_deliveries SET due_at = ?, ticket_id = ?, attempts = ? WHERE id = ?",
            Timestamp.from(Instant.now().plusSeconds(delaySeconds)), ticket,
            if (ticket == job.ticket) job.attempts + 1 else 0, job.id
        )
    }

    private fun UUID.bytes(): ByteArray = ByteBuffer.allocate(16)
        .putLong(mostSignificantBits).putLong(leastSignificantBits).array()
}
