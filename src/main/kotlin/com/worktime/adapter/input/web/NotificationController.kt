package com.worktime.adapter.input.web

import com.worktime.adapter.input.web.dto.notification.NotificationListResponse
import com.worktime.adapter.input.web.dto.notification.NotificationResponse
import com.worktime.application.service.NotificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * 알림 API Controller
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notification", description = "알림 API")
class NotificationController(
    private val notificationService: NotificationService
) {
    
    /**
     * 현재 로그인한 사용자 ID를 가져옵니다
     */
    private fun getCurrentUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
        return UUID.fromString(authentication.name)
    }
    
    @GetMapping
    @Operation(summary = "알림 목록 조회", description = "로그인한 사용자의 모든 알림을 최신순으로 조회합니다")
    fun getNotifications(): ResponseEntity<NotificationListResponse> {
        val userId = getCurrentUserId()
        
        val notifications = notificationService.getNotificationsByUserId(userId)
        val unreadCount = notificationService.getUnreadCount(userId)
        
        val response = NotificationListResponse.from(notifications, unreadCount)
        return ResponseEntity.ok(response)
    }
    
    @GetMapping("/unread-count")
    @Operation(summary = "읽지 않은 알림 개수", description = "읽지 않은 알림의 개수를 조회합니다")
    fun getUnreadCount(): ResponseEntity<Map<String, Long>> {
        val userId = getCurrentUserId()
        val count = notificationService.getUnreadCount(userId)
        
        return ResponseEntity.ok(mapOf("count" to count))
    }
    
    @PostMapping("/{id}/read")
    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음 상태로 변경합니다")
    fun markAsRead(@PathVariable id: UUID): ResponseEntity<Void> {
        val userId = getCurrentUserId()
        notificationService.markAsRead(id, userId)
        
        return ResponseEntity.ok().build()
    }
    
    @PostMapping("/read-all")
    @Operation(summary = "모든 알림 읽음 처리", description = "사용자의 모든 알림을 읽음 상태로 변경합니다")
    fun markAllAsRead(): ResponseEntity<Map<String, Int>> {
        val userId = getCurrentUserId()
        val count = notificationService.markAllAsRead(userId)
        
        return ResponseEntity.ok(mapOf("updatedCount" to count))
    }
}
