package com.worktime.adapter.output.push

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "worktime.push", name = ["enabled"], havingValue = "true")
class PushScheduling

@Component
@ConditionalOnProperty(prefix = "worktime.push", name = ["enabled"], havingValue = "true")
class ExpoPushDispatcher(
    private val queue: PushQueue,
    private val json: ObjectMapper,
    @Value("\${worktime.push.expo-access-token:}") private val accessToken: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build()

    @Scheduled(fixedDelay = 5000)
    fun dispatch() {
        for (job in queue.due(Timestamp.from(Instant.now()))) {
            if (!queue.claim(job, Timestamp.from(Instant.now()))) continue
            if (!queue.isRegistered(job)) {
                queue.finish(job)
                continue
            }
            try {
                if (job.ticket == null) send(job) else checkReceipt(job, job.ticket)
            } catch (_: IOException) {
                retry(job)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    private fun send(job: PushQueue.Delivery) {
        val data = post("send", mapOf(
            "to" to job.token,
            "title" to "Worktime",
            "body" to "새 알림이 도착했습니다. 앱에서 확인해 주세요.",
            "channelId" to "worktime-updates",
            "sound" to "default",
            "ttl" to 3600
        ))
        if (data.path("status").asText() == "ok") {
            val ticket = data.path("id").asText()
            if (ticket.isBlank()) throw IOException("Missing Expo push ticket")
            queue.reschedule(job, 900, ticket)
        } else {
            handleError(job, data)
        }
    }

    private fun checkReceipt(job: PushQueue.Delivery, ticket: String) {
        val receipt = post("getReceipts", mapOf("ids" to listOf(ticket))).path(ticket)
        when (receipt.path("status").asText()) {
            "ok" -> queue.finish(job)
            "error" -> handleError(job, receipt)
            else -> retry(job)
        }
    }

    private fun handleError(job: PushQueue.Delivery, result: JsonNode) {
        when (result.path("details").path("error").asText()) {
            "DeviceNotRegistered" -> queue.discardDevice(job)
            "MessageRateExceeded" -> if (job.ticket == null) {
                retry(job)
                return
            }
            else -> log.warn("Expo rejected push delivery {}", job.id)
        }
        queue.finish(job)
    }

    private fun retry(job: PushQueue.Delivery) {
        if (job.attempts >= 4) {
            log.warn("Push delivery {} exhausted retries", job.id)
            queue.finish(job)
        } else {
            queue.reschedule(job, 60L shl job.attempts)
        }
    }

    private fun post(path: String, payload: Map<String, Any>): JsonNode {
        val request = HttpRequest.newBuilder(URI.create("https://exp.host/--/api/v2/push/$path"))
            .timeout(Duration.ofSeconds(8))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
        if (accessToken.isNotBlank()) request.header("Authorization", "Bearer $accessToken")
        val response = http.send(
            request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(),
            HttpResponse.BodyHandlers.ofString()
        )
        if (response.statusCode() == 429 || response.statusCode() >= 500) throw IOException("Expo unavailable")
        val data = json.readTree(response.body())?.path("data")
        if (response.statusCode() !in 200..299) return json.createObjectNode().put("status", "error")
        return data?.takeUnless { it.isMissingNode || it.isNull } ?: throw IOException("Missing Expo response data")
    }
}
