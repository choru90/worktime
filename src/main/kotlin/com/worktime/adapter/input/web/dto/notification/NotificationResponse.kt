package com.worktime.adapter.input.web.dto.notification

import com.worktime.domain.model.Notification
import com.worktime.domain.model.NotificationType
import java.time.OffsetDateTime
import java.util.*

/**
 * 알림 응답 DTO
 */
data class NotificationResponse(
    val id: UUID,
    val type: NotificationType,
    val title: String,
    val message: String,
    val referenceId: UUID?,
    val isRead: Boolean,
    val createdAt: OffsetDateTime
) {
    companion object {
        fun from(notification: Notification): NotificationResponse {
            return NotificationResponse(
                id = notification.id,
                type = notification.type,
                title = notification.title,
                message = notification.message,
                referenceId = notification.referenceId,
                isRead = notification.isRead,
                createdAt = notification.createdAt
            )
        }
    }
}

/**
 * 알림 목록 응답 DTO
 */
data class NotificationListResponse(
    val notifications: List<NotificationResponse>,
    val unreadCount: Long
) {
    companion object {
        fun from(notifications: List<Notification>, unreadCount: Long): NotificationListResponse {
            return NotificationListResponse(
                notifications = notifications.map { NotificationResponse.from(it) },
                unreadCount = unreadCount
            )
        }
    }
}
