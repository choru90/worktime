package com.worktime

import com.fasterxml.jackson.databind.ObjectMapper
import com.worktime.adapter.output.push.ExpoPushDispatcher
import com.worktime.adapter.output.push.PushQueue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Local review evidence: all HTTP is mocked; no Expo request or real token is used. */
class PushBoundaryReviewTest {
    private val queue = mock<PushQueue>()
    private val http = mock<HttpClient>()
    private val job = PushQueue.Delivery(123, "ExpoPushToken[synthetic-review]", byteArrayOf(1), 0, null)
    private val dispatcher = ExpoPushDispatcher(queue, ObjectMapper(), "")
    init {
        ExpoPushDispatcher::class.java.getDeclaredField("http").apply { isAccessible = true }.set(dispatcher, http)
        whenever(queue.due(any())).thenReturn(listOf(job))
        whenever(queue.claim(eq(job), any())).thenReturn(true)
        whenever(queue.isRegistered(job)).thenReturn(true)
    }
    private fun response(body: String, status: Int = 200) {
        val reply = mock<HttpResponse<String>>()
        whenever(reply.statusCode()).thenReturn(status)
        whenever(reply.body()).thenReturn(body)
        whenever(http.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())).thenReturn(reply)
    }
    @Test fun `unregistered binding is dropped without sending`() {
        whenever(queue.isRegistered(job)).thenReturn(false)
        dispatcher.dispatch()
        verify(queue).finish(job)
        verifyNoInteractions(http)
    }
    @Test fun `claimed job is sent only once by the claimant`() {
        whenever(queue.claim(eq(job), any())).thenReturn(false)
        dispatcher.dispatch()
        verifyNoInteractions(http)
    }
    @Test fun `Expo acceptance schedules receipt rather than claiming delivery`() {
        response("""{"data":{"status":"ok","id":"synthetic-ticket"}}""")
        dispatcher.dispatch()
        verify(queue).reschedule(job, 900, "synthetic-ticket")
        verify(queue, never()).finish(any())
    }
    @Test fun `invalid device token is unregistered and job removed`() {
        response("""{"data":{"status":"error","details":{"error":"DeviceNotRegistered"}}}""")
        dispatcher.dispatch()
        verify(queue).discardDevice(job)
        verify(queue).finish(job)
    }
    @Test fun `rate limit retries without deleting valid device`() {
        response("{}", 429)
        dispatcher.dispatch()
        verify(queue).reschedule(job, 60, null)
        verify(queue, never()).discardDevice(any())
    }
    @Test fun `transient server failure retries`() {
        response("{}", 503)
        dispatcher.dispatch()
        verify(queue).reschedule(job, 60, null)
    }
    @Test fun `network error retries without exposing payload`() {
        whenever(http.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())).thenThrow(IOException("synthetic offline"))
        dispatcher.dispatch()
        verify(queue).reschedule(job, 60, null)
    }
    @Test fun `retry exhaustion drops job rather than retrying indefinitely`() {
        val exhausted = job.copy(attempts = 4)
        whenever(queue.due(any())).thenReturn(listOf(exhausted))
        whenever(queue.claim(eq(exhausted), any())).thenReturn(true)
        whenever(queue.isRegistered(exhausted)).thenReturn(true)
        response("{}", 503)
        dispatcher.dispatch()
        verify(queue).finish(exhausted)
        verify(queue, never()).reschedule(any(), any(), anyOrNull())
    }
    @Test fun `successful receipt removes pending job`() {
        val pending = job.copy(ticket = "synthetic-ticket")
        whenever(queue.due(any())).thenReturn(listOf(pending))
        whenever(queue.claim(eq(pending), any())).thenReturn(true)
        whenever(queue.isRegistered(pending)).thenReturn(true)
        response("""{"data":{"synthetic-ticket":{"status":"ok"}}}""")
        dispatcher.dispatch()
        verify(queue).finish(pending)
    }
    @Test fun `not ready receipt is retried`() {
        val pending = job.copy(ticket = "synthetic-ticket")
        whenever(queue.due(any())).thenReturn(listOf(pending))
        whenever(queue.claim(eq(pending), any())).thenReturn(true)
        whenever(queue.isRegistered(pending)).thenReturn(true)
        response("""{"data":{}}""")
        dispatcher.dispatch()
        verify(queue).reschedule(pending, 60, "synthetic-ticket")
    }
}
